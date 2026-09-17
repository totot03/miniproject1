package com.pharmaprice.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.auth.dto.LoginRequest;
import com.pharmaprice.auth.dto.LoginResponse;
import com.pharmaprice.auth.dto.RefreshRequest;
import com.pharmaprice.auth.dto.SignupRequest;
import com.pharmaprice.auth.dto.SignupResponse;
import com.pharmaprice.auth.dto.UserSummary;
import com.pharmaprice.auth.exception.AuthenticationFailedException;
import com.pharmaprice.auth.exception.EmailAlreadyExistsException;
import com.pharmaprice.auth.service.AuthService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code docs/API.md} §2 signup/login/refresh 응답 형태·상태코드를 검증하는
 * 컨트롤러 슬라이스 테스트. logout/me는 인증이 필요해 이 슬라이스로 다루지
 * 않고 {@code AuthIntegrationTest}(@SpringBootTest)에서 검증한다.
 *
 * <p>{@code AuthExceptionHandler}는 {@code @RestControllerAdvice}라 {@code @WebMvcTest}가
 * {@code controllers}로 좁혀도 자동으로 함께 로드된다(Spring Boot의
 * {@code WebMvcTypeExcludeFilter}가 컨트롤러 어드바이스는 항상 포함시킨다) —
 * 별도 {@code @Import} 없이 409/401 바디 검증이 그대로 통과하는 것으로 확인한다.</p>
 */
@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void signupReturns201WithApiSpecFields() throws Exception {
        given(authService.signup(any(SignupRequest.class))).willReturn(
            new SignupResponse(42L, "minji@example.com", "민지", UserRole.USER, OffsetDateTime.parse("2026-09-15T13:12:33+09:00")));

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"minji@example.com","password":"Password123","nickname":"민지"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.email").value("minji@example.com"))
            .andExpect(jsonPath("$.nickname").value("민지"))
            .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void signupReturns400WhenEmailIsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"not-an-email","password":"Password123","nickname":"민지"}
                    """))
            .andExpect(status().isBadRequest());

        verify(authService, never()).signup(any());
    }

    @Test
    void signupReturns409WithEmailAlreadyExistsCodeOnDuplicate() throws Exception {
        given(authService.signup(any(SignupRequest.class)))
            .willThrow(new EmailAlreadyExistsException("이미 사용 중인 이메일입니다."));

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"dup@example.com","password":"Password123","nickname":"민지"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void loginReturns200WithApiSpecFields() throws Exception {
        given(authService.login(any(LoginRequest.class))).willReturn(
            new LoginResponse("access-token", "refresh-token", 1800L, new UserSummary(42L, "민지", UserRole.USER)));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"minji@example.com","password":"Password123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
            .andExpect(jsonPath("$.expiresIn").value(1800))
            .andExpect(jsonPath("$.user.id").value(42))
            .andExpect(jsonPath("$.user.nickname").value("민지"))
            .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    void loginReturns401WithUnauthenticatedCodeOnFailure() throws Exception {
        given(authService.login(any(LoginRequest.class)))
            .willThrow(new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다."));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"minji@example.com","password":"wrong"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void refreshReturns200WithRotatedTokens() throws Exception {
        given(authService.refresh(any(RefreshRequest.class))).willReturn(
            new LoginResponse("new-access-token", "new-refresh-token", 1800L, new UserSummary(42L, "민지", UserRole.USER)));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"old-refresh-token"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new-access-token"))
            .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    void refreshReturns401WithUnauthenticatedCodeOnInvalidToken() throws Exception {
        given(authService.refresh(any(RefreshRequest.class)))
            .willThrow(new AuthenticationFailedException("유효하지 않은 refresh 토큰입니다."));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"invalid-or-expired"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
