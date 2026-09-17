package com.pharmaprice.report.service;

import com.pharmaprice.report.exception.FileStorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code app.upload-dir}(기본 프로젝트 루트의 {@code uploads/}) 아래에
 * {@code {yyyy}/{MM}/{uuid}.{ext}} 형태로 저장한다({@code docs/ROADMAP.md} T-27).
 *
 * <p>원본 파일명은 경로에 쓰지 않는다(경로 조작 방지) - 파일명은 항상
 * {@link UUID#randomUUID()}로 새로 만든다. {@code store}/{@code load} 모두
 * 계산된 대상 경로가 루트 밖으로 벗어나지 않는지 확인한다: 지금 입력(UUID +
 * 고정 확장자)만으로는 탈출이 불가능하지만, {@code load}는 DB의 {@code stored_path}
 * 컬럼값을 그대로 신뢰하는 지점이라 방어선으로 남겨둔다.</p>
 */
@Component
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private final UploadProperties uploadProperties;

    @Override
    public String store(byte[] content, String extension) {
        LocalDate today = LocalDate.now();
        String relativePath = "%04d/%02d/%s.%s".formatted(
            today.getYear(), today.getMonthValue(), UUID.randomUUID(), extension);

        Path target = resolveWithinRoot(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new FileStorageException("파일 저장에 실패했습니다: " + relativePath, e);
        }
        return relativePath;
    }

    @Override
    public byte[] load(String storagePath) {
        Path target = resolveWithinRoot(storagePath);
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new FileStorageException("파일 조회에 실패했습니다: " + storagePath, e);
        }
    }

    private Path resolveWithinRoot(String relativePath) {
        Path root = Path.of(uploadProperties.uploadDir()).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new FileStorageException("허용되지 않은 저장 경로입니다: " + relativePath, null);
        }
        return target;
    }
}
