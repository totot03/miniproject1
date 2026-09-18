package com.pharmaprice.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 짧은 traceId를 만들어 {@link MDC}에 넣는다. {@code common.dto.ErrorResponse}가
 * 같은 값을 읽어 응답에 싣고, {@code application.yml}의 {@code logging.pattern.level}이
 * 같은 값을 로그 줄에 찍어 응답과 로그가 traceId로 상관관계를 갖게 한다
 * (docs/ROADMAP.md T-35 3번).
 *
 * <p>일부러 {@code @Component}를 붙이지 않는다 —
 * {@code JwtAuthenticationFilter}와 같은 이유(자바독 참고)로, {@code @Component}가 붙은
 * {@code jakarta.servlet.Filter}는 {@code @WebMvcTest} 슬라이스가
 * {@code excludeAutoConfiguration}과 무관하게 자동으로 끌어온다. 대신
 * {@code TraceIdFilterConfig}가 {@code FilterRegistrationBean}으로 명시적으로 등록한다.</p>
 */
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        MDC.put(TRACE_ID_MDC_KEY, UUID.randomUUID().toString().substring(0, 8));
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
