package com.pharmaprice.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pharmaprice.report.exception.FileStorageException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code docs/ROADMAP.md} T-27 저장 경로 규칙(`{yyyy}/{MM}/{uuid}.{ext}`)과
 * 경로 조작 방지를 실제 디스크 I/O로 검증한다.
 */
class LocalFileStorageServiceTest {

    @TempDir
    private Path uploadDir;

    private LocalFileStorageService service;

    @BeforeEach
    void setUp() {
        service = new LocalFileStorageService(new UploadProperties(uploadDir.toString()));
    }

    @Test
    void 저장_경로는_yyyy_MM_uuid_확장자_형식이다() {
        String relativePath = service.store("hello".getBytes(StandardCharsets.UTF_8), "jpg");

        LocalDate today = LocalDate.now();
        String expectedPrefix = "%04d/%02d/".formatted(today.getYear(), today.getMonthValue());
        assertThat(relativePath).startsWith(expectedPrefix);
        assertThat(relativePath).matches(Pattern.quote(expectedPrefix) + "[0-9a-f-]{36}\\.jpg");
    }

    @Test
    void 존재하지_않는_yyyy_MM_디렉터리를_자동생성한다() {
        String relativePath = service.store("content".getBytes(StandardCharsets.UTF_8), "png");

        assertThat(uploadDir.resolve(relativePath)).exists();
    }

    @Test
    void store한_내용을_load로_동일하게_읽는다() {
        byte[] original = "receipt-image-bytes".getBytes(StandardCharsets.UTF_8);

        String relativePath = service.store(original, "webp");
        byte[] loaded = service.load(relativePath);

        assertThat(loaded).isEqualTo(original);
    }

    @Test
    void 루트를_벗어나는_경로는_거부한다() {
        assertThatThrownBy(() -> service.load("../../etc/passwd"))
            .isInstanceOf(FileStorageException.class);
    }

    @Test
    void 존재하지_않는_파일을_읽으면_FileStorageException() {
        assertThatThrownBy(() -> service.load("2099/01/does-not-exist.jpg"))
            .isInstanceOf(FileStorageException.class);
    }
}
