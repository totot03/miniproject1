package com.pharmaprice.report.controller;

import com.pharmaprice.auth.security.AuthPrincipal;
import com.pharmaprice.report.dto.UploadResponse;
import com.pharmaprice.report.dto.UploadedFileContent;
import com.pharmaprice.report.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code docs/API.md} §6 업로드 2개 엔드포인트({@code docs/ROADMAP.md} T-27).
 *
 * <p>둘 다 permitAll 목록에 없어 {@code SecurityConfig}의 "나머지 authenticated()"
 * 규칙에 걸린다 - 별도 설정 불필요. {@code purpose} 파트는 현재 유일값 "RECEIPT"뿐이고
 * DB에 저장하지도 않아, 존재 여부만 요구하고 값은 엄격히 검증하지 않는다.</p>
 */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@AuthenticationPrincipal AuthPrincipal principal,
                                  @RequestParam("file") MultipartFile file,
                                  @RequestParam("purpose") String purpose) {
        return uploadService.upload(principal.userId(), file);
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable Long fileId) {
        UploadedFileContent file = uploadService.getFileContent(fileId, principal.userId(), principal.role());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .body(file.content());
    }
}
