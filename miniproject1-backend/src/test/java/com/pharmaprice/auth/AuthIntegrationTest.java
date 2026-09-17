package com.pharmaprice.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pharmaprice.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code docs/ROADMAP.md} T-24 완료 판정을 실제 {@code SecurityFilterChain}·DB로
 * 검증한다. {@code SecurityConfigIntegrationTest}와 동일하게 {@code @WebMvcTest}
 * 슬라이스가 아니라 {@code @SpringBootTest} 기반이어야 한다 — logout/me는 필터
 * 체인이 실제로 인증을 통과시켜야 {@code @AuthenticationPrincipal}이 채워진다.
 */
@AutoConfigureMockMvc
class AuthIntegrationTest extends AbstractIntegrationTest {

    /** {@code tools/seed-generator/build_master_seed.py}의 ADMIN_PASSWORD 평문. */
    private static final String ADMIN_PASSWORD = "Admin1234!";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullFlow_signupLoginMeLogoutThenRefreshIsUnauthenticated() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@example.com";
        String password = "Password123";

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"%s","nickname":"플로우"}
                    """.formatted(email, password)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(email));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"%s"}
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();
        String responseBody = loginResult.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.accessToken");
        String refreshToken = JsonPath.read(responseBody, "$.refreshToken");

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.reportCount").value(0));

        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"%s"}
                    """.formatted(refreshToken)))
            .andExpect(status().isNoContent());

        // 로그아웃은 멱등해야 한다 — 같은 토큰으로 다시 호출해도 204.
        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"%s"}
                    """.formatted(refreshToken)))
            .andExpect(status().isNoContent());

        // revoke된 refresh 토큰으로 재발급을 시도하면 401.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"%s"}
                    """.formatted(refreshToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void adminSeedLoginReturnsAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"admin@example.com","password":"%s"}
                    """.formatted(ADMIN_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void meWithoutTokenIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
