package com.pharmaprice.common.config;

import com.pharmaprice.common.web.TraceIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * {@link TraceIdFilter}를 Spring Security 필터 체인보다도 먼저 돌게 등록한다 —
 * 그래야 {@code JwtAuthenticationEntryPoint}(Security 필터 안에서 401을 직접 쓰는 지점)도
 * MDC의 traceId를 볼 수 있다(docs/ROADMAP.md T-35 3번).
 */
@Configuration
class TraceIdFilterConfig {

    @Bean
    FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
