package com.pharmaprice.report.dto;

import java.time.OffsetDateTime;

/**
 * {@code docs/API.md} §6 {@code POST /uploads} 응답.
 */
public record UploadResponse(
    Long id,
    String originalName,
    String contentType,
    long sizeBytes,
    String url,
    OffsetDateTime createdAt
) {
}
