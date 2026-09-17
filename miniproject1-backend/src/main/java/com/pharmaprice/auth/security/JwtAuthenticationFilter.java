package com.pharmaprice.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer} 헤더의 access 토큰을 검증해 {@code SecurityContext}
 * 에 인증 정보를 세팅한다.
 *
 * <p>토큰이 없거나 검증에 실패해도 예외를 던지지 않고 그대로 다음 필터로 넘긴다.
 * permitAll 경로는 인증 없이 통과하고, 인증이 필요한 경로는 {@code SecurityContext}
 * 가 비어있으므로 {@code AuthorizationFilter} 가 자연스럽게 미인증으로 판단해
 * {@code AuthenticationEntryPoint}(401) 로 이어진다.</p>
 *
 * <p>일부러 {@code @Component}를 붙이지 않는다 — 이 필터는
 * {@code jakarta.servlet.Filter}를 구현하므로, {@code @Component}가 붙으면
 * {@code @WebMvcTest} 슬라이스가 (보안 오토컨피그 제외 여부와 무관하게) 이
 * 필터를 자동으로 끌어오면서 슬라이스에는 없는 {@link JwtTokenProvider}
 * 의존성 때문에 컨텍스트 로딩이 깨진다. {@code SecurityConfig}가 직접
 * {@code new}로 생성해 필터 체인에 등록한다.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        extractToken(request).ifPresent(token -> authenticate(token, request));
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            AuthPrincipal principal = jwtTokenProvider.validateAndGetPrincipal(token);
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException e) {
            log.debug("JWT 인증 실패: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }
}
