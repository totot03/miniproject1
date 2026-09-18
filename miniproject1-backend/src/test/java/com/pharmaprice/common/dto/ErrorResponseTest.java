package com.pharmaprice.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.pharmaprice.common.web.TraceIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * {@code docs/ROADMAP.md} T-35 3번 — traceId는 MDC에서 꺼내야 응답과 로그가 상관관계를
 * 갖는다. {@link TraceIdFilter}가 실제로 MDC에 값을 넣는지는 컨트롤러 요청 스레드
 * 안에서만 확인 가능하므로, 여기서는 그 계약(=MDC에 값이 있으면 그대로 쓰고, 없으면
 * 폴백한다)만 순수 단위 테스트로 고정한다.
 */
class ErrorResponseTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void MDC에_traceId가있으면_그값을그대로쓴다() {
        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "abc12345");

        ErrorResponse response = ErrorResponse.of("VALIDATION_FAILED", "메시지");

        assertThat(response.traceId()).isEqualTo("abc12345");
    }

    @Test
    void MDC가비어있으면_임의의traceId로폴백한다() {
        MDC.clear();

        ErrorResponse response = ErrorResponse.of("VALIDATION_FAILED", "메시지");

        assertThat(response.traceId()).isNotNull();
        assertThat(response.traceId()).isNotBlank();
    }
}
