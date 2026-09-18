package com.pharmaprice.common.dto;

import com.pharmaprice.common.web.TraceIdFilter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * {@code docs/API.md} §1.2 에러 포맷. {@code fieldErrors} 는 검증 실패일
 * 때만 채워지고, 그 외에는 {@code null} 이다.
 *
 * <p>{@code traceId}는 {@link TraceIdFilter}가 요청마다 MDC에 넣어둔 값을 그대로
 * 쓴다 — 응답과 로그(application.yml의 {@code logging.pattern.level})가 같은 값을
 * 공유해야 사용자가 알려준 traceId로 로그를 바로 찾을 수 있다(docs/ROADMAP.md T-35
 * 3번). MDC가 비어있는 경우(요청 스레드 밖에서 호출되는 등)에만 방어적으로 새
 * UUID를 만든다.</p>
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
        return of(code, message, null);
    }

    public static ErrorResponse of(String code, String message, List<FieldError> fieldErrors) {
        return new ErrorResponse(code, message, fieldErrors, currentTraceId(), OffsetDateTime.now());
    }

    private static String currentTraceId() {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        return traceId != null ? traceId : UUID.randomUUID().toString().substring(0, 8);
    }
}
