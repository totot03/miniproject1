package com.pharmaprice.report.dto;

/**
 * {@code GET /uploads/{fileId}} 응답용 파일 바이너리. 서비스 -> 컨트롤러 전달 전용.
 */
public record UploadedFileContent(
    byte[] content,
    String contentType,
    String originalName
) {
}
