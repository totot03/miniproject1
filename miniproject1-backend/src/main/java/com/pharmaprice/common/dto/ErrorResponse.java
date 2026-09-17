package com.pharmaprice.common.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code docs/API.md} §1.2 에러 포맷. {@code fieldErrors} 는 검증 실패일
 * 때만 채워지고, 그 외에는 {@code null} 이다.
 */
public record ErrorResponse(
    String code,
    String message,
    List<FieldError> fieldErrors,
    String traceId,
    OffsetDateTime timestamp
) {

    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null, generateTraceId(), OffsetDateTime.now());
    }

    private static String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
