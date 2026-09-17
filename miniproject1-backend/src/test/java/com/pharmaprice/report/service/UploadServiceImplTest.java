package com.pharmaprice.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.report.domain.UploadedFile;
import com.pharmaprice.report.dto.UploadResponse;
import com.pharmaprice.report.dto.UploadedFileContent;
import com.pharmaprice.report.exception.UnsupportedFileTypeException;
import com.pharmaprice.report.exception.UploadedFileNotFoundException;
import com.pharmaprice.report.repository.UploadedFileRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

/**
 * {@code docs/ROADMAP.md} T-27 {@link UploadServiceImpl} 비즈니스 로직 검증.
 * {@link FileStorageService}는 Mockito 목이다 - 실제 디스크 I/O는
 * {@link LocalFileStorageServiceTest}가 담당한다.
 */
class UploadServiceImplTest {

    private static final long USER_ID = 100L;
    private static final long OTHER_USER_ID = 200L;
    private static final long FILE_ID = 55L;

    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final UploadedFileRepository uploadedFileRepository = mock(UploadedFileRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);

    private UploadServiceImpl service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new UploadServiceImpl(appUserRepository, uploadedFileRepository, fileStorageService);

        user = AppUser.builder().id(USER_ID).email("user@test.com").passwordHash("HASHED").nickname("닉네임").build();

        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(uploadedFileRepository.save(any(UploadedFile.class))).willAnswer(inv -> {
            UploadedFile arg = inv.getArgument(0);
            return UploadedFile.builder()
                .id(FILE_ID)
                .originalName(arg.getOriginalName())
                .storedPath(arg.getStoredPath())
                .contentType(arg.getContentType())
                .sizeBytes(arg.getSizeBytes())
                .uploadedBy(arg.getUploadedBy())
                .build();
        });
    }

    private static byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    }

    @Test
    void 정상_업로드시_서버가_감지한_contentType으로_저장하고_id를_반환한다() {
        given(fileStorageService.store(any(byte[].class), anyString())).willReturn("2026/09/uuid.jpg");
        var file = new MockMultipartFile("file", "receipt.jpg", "application/octet-stream", jpegBytes());

        UploadResponse response = service.upload(USER_ID, file);

        assertThat(response.id()).isEqualTo(FILE_ID);
        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.url()).isEqualTo("/api/v1/uploads/" + FILE_ID);

        ArgumentCaptor<UploadedFile> captor = ArgumentCaptor.forClass(UploadedFile.class);
        verify(uploadedFileRepository).save(captor.capture());
        // 클라이언트가 보낸 Content-Type("application/octet-stream")이 아니라
        // 매직바이트로 감지한 값("image/jpeg")이 저장돼야 한다.
        assertThat(captor.getValue().getContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void jpg로_위장한_PDF는_저장을_시도하지_않고_415에_해당하는_예외를_던진다() {
        byte[] pdfBytes = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", pdfBytes);

        assertThatThrownBy(() -> service.upload(USER_ID, file))
            .isInstanceOf(UnsupportedFileTypeException.class);

        verify(fileStorageService, never()).store(any(), anyString());
        verify(uploadedFileRepository, never()).save(any());
    }

    @Test
    void 업로더_본인은_자신의_파일을_조회할_수_있다() {
        UploadedFile uploadedFile = UploadedFile.builder()
            .id(FILE_ID).originalName("r.jpg").storedPath("2026/09/uuid.jpg")
            .contentType("image/jpeg").sizeBytes(100L).uploadedBy(user).build();
        given(uploadedFileRepository.findById(FILE_ID)).willReturn(Optional.of(uploadedFile));
        given(fileStorageService.load("2026/09/uuid.jpg")).willReturn(new byte[]{1, 2, 3});

        UploadedFileContent content = service.getFileContent(FILE_ID, USER_ID, UserRole.USER);

        assertThat(content.content()).containsExactly(1, 2, 3);
        assertThat(content.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void ADMIN은_타인의_파일도_조회할_수_있다() {
        UploadedFile uploadedFile = UploadedFile.builder()
            .id(FILE_ID).originalName("r.jpg").storedPath("2026/09/uuid.jpg")
            .contentType("image/jpeg").sizeBytes(100L).uploadedBy(user).build();
        given(uploadedFileRepository.findById(FILE_ID)).willReturn(Optional.of(uploadedFile));
        given(fileStorageService.load("2026/09/uuid.jpg")).willReturn(new byte[]{1});

        UploadedFileContent content = service.getFileContent(FILE_ID, OTHER_USER_ID, UserRole.ADMIN);

        assertThat(content).isNotNull();
    }

    @Test
    void 타인이_USER권한으로_조회하면_403에_해당하는_예외를_던진다() {
        UploadedFile uploadedFile = UploadedFile.builder()
            .id(FILE_ID).originalName("r.jpg").storedPath("2026/09/uuid.jpg")
            .contentType("image/jpeg").sizeBytes(100L).uploadedBy(user).build();
        given(uploadedFileRepository.findById(FILE_ID)).willReturn(Optional.of(uploadedFile));

        assertThatThrownBy(() -> service.getFileContent(FILE_ID, OTHER_USER_ID, UserRole.USER))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 존재하지_않는_파일을_조회하면_예외를_던진다() {
        given(uploadedFileRepository.findById(FILE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFileContent(FILE_ID, USER_ID, UserRole.USER))
            .isInstanceOf(UploadedFileNotFoundException.class);
    }
}
