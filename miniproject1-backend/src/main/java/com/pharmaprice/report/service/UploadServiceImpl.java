package com.pharmaprice.report.service;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.report.domain.UploadedFile;
import com.pharmaprice.report.dto.UploadResponse;
import com.pharmaprice.report.dto.UploadedFileContent;
import com.pharmaprice.report.exception.FileStorageException;
import com.pharmaprice.report.exception.UnsupportedFileTypeException;
import com.pharmaprice.report.exception.UploadedFileNotFoundException;
import com.pharmaprice.report.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code docs/ROADMAP.md} T-27 구현체.
 *
 * <p>업로드된 파일의 실제 타입은 항상 매직바이트로 판정한다({@link DetectedImageType})
 * - 클라이언트가 보낸 파일명·{@code Content-Type} 헤더는 저장 확장자·DB의
 * {@code content_type} 컬럼 어디에도 쓰지 않는다({@code .jpg}로 위장한 PDF 방지).</p>
 */
@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private final AppUserRepository appUserRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public UploadResponse upload(Long userId, MultipartFile file) {
        byte[] content = readBytes(file);
        DetectedImageType type = DetectedImageType.detect(content)
            .orElseThrow(() -> new UnsupportedFileTypeException(
                "지원하지 않는 파일 형식입니다: " + file.getOriginalFilename()));

        String storedPath = fileStorageService.store(content, type.extension());

        // JWT로 인증된 본인 id 조회 - AuthServiceImpl.me()와 동일하게 신뢰하는 bare orElseThrow.
        AppUser user = appUserRepository.findById(userId).orElseThrow();

        UploadedFile uploadedFile = UploadedFile.builder()
            .originalName(file.getOriginalFilename())
            .storedPath(storedPath)
            .contentType(type.contentType())
            .sizeBytes(content.length)
            .uploadedBy(user)
            .build();
        uploadedFile = uploadedFileRepository.save(uploadedFile);

        return toResponse(uploadedFile);
    }

    @Override
    @Transactional(readOnly = true)
    public UploadedFileContent getFileContent(Long fileId, Long requesterId, UserRole requesterRole) {
        UploadedFile uploadedFile = uploadedFileRepository.findById(fileId)
            .orElseThrow(() -> new UploadedFileNotFoundException("uploaded file not found: " + fileId));

        boolean isOwner = uploadedFile.getUploadedBy() != null
            && uploadedFile.getUploadedBy().getId().equals(requesterId);
        if (!isOwner && requesterRole != UserRole.ADMIN) {
            throw new AccessDeniedException("본인이 업로드한 파일만 조회할 수 있습니다.");
        }

        byte[] content = fileStorageService.load(uploadedFile.getStoredPath());
        return new UploadedFileContent(content, uploadedFile.getContentType(), uploadedFile.getOriginalName());
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new FileStorageException("업로드 파일을 읽을 수 없습니다.", e);
        }
    }

    private UploadResponse toResponse(UploadedFile uploadedFile) {
        return new UploadResponse(
            uploadedFile.getId(),
            uploadedFile.getOriginalName(),
            uploadedFile.getContentType(),
            uploadedFile.getSizeBytes(),
            "/api/v1/uploads/" + uploadedFile.getId(),
            uploadedFile.getCreatedAt());
    }
}
