package com.pharmaprice.auth.security;

import tools.jackson.databind.ObjectMapper;
import com.pharmaprice.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증 실패(토큰 없음/만료/서명 불일치) 시 401을 {@code docs/API.md} §1.2
 * 포맷으로 응답한다. 이 지점은 {@code DispatcherServlet} 이전에 동작하므로
 * {@code @ExceptionHandler} 로는 잡히지 않는다 — 직접 JSON 을 write 한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.of("UNAUTHENTICATED", "인증이 필요합니다.");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
