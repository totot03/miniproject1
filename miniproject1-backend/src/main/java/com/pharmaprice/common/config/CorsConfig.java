package com.pharmaprice.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 개발 CORS 허용.
 *
 * <p>프론트(Next.js dev server, 기본 3000 — 포트가 이미 쓰이고 있으면 3001로
 * 자동 전환됨)와 백엔드(8080)가 서로 다른 포트에서 뜨므로, 브라우저는 모든
 * API 호출을 크로스오리진으로 취급해 CORS 프리플라이트를 요구한다. 이 설정이
 * 없으면 프론트가 어떤 API도 호출하지 못한다(T-16에서 실제로 확인된 문제 —
 * {@code GET /api/v1/regions} 호출이 브라우저에서 차단됨).</p>
 *
 * <p>{@code allowedOriginPatterns}를 쓰는 이유: Next dev server가 포트를
 * 자동으로 바꿀 수 있어(3000 사용 중이면 3001) 고정 포트 하나만 허용하면
 * 쉽게 깨진다. {@code allowedOrigins("*")}와 달리 패턴은 와일드카드를 쓰면서도
 * {@code allowCredentials(true)}와 함께 쓸 수 있다(T-25 인증 토큰 대비).</p>
 *
 * <p>{@code SecurityConfig}가 아직 없어(T-23 몫) 지금은 Spring Security
 * 기본 설정이 모든 요청에 401을 반환한다 — 이 CORS 설정과는 별개 문제이며,
 * {@code DrugController} 등이 이미 문서화한 대로 T-23에서 해소된다.</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("http://localhost:*")
            .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
