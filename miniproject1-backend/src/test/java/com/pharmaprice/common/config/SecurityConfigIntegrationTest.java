package com.pharmaprice.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.auth.security.JwtTokenProvider;
import com.pharmaprice.drug.repository.DrugRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code docs/ROADMAP.md} T-23 완료판정 4개 시나리오를 실제 {@code SecurityFilterChain}
 * 전체를 태워 검증한다. {@code @WebMvcTest} 슬라이스는 보안 오토컨피그를 제외하므로
 * 이 검증에는 쓸 수 없다 — {@code @SpringBootTest} 기반이어야 한다.
 */
@AutoConfigureMockMvc
class SecurityConfigIntegrationTest extends AbstractIntegrationTest {

    /** src/test/resources/application-test.yml 의 app.jwt.secret 과 반드시 같아야 한다. */
    private static final String TEST_SECRET = "test-jwt-secret-key-for-integration-tests-only-32-bytes-min";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private DrugRepository drugRepository;

    @Test
    void postWithoutTokenIsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/price-reports"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void userTokenCannotAccessAdminPath() throws Exception {
        String userToken = jwtTokenProvider.generateAccessToken(1L, UserRole.USER);

        mockMvc.perform(get("/api/v1/admin/reports")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void expiredTokenIsUnauthenticated() throws Exception {
        String expiredToken = buildExpiredAccessToken();

        mockMvc.perform(get("/api/v1/admin/reports")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void searchIsPubliclyAccessibleWithoutToken() throws Exception {
        Long drugId = drugRepository.findByItemSeq("SEED00001").orElseThrow().getId();

        mockMvc.perform(get("/api/v1/search")
                .param("drugId", String.valueOf(drugId))
                .param("regionCode", "110001"))
            .andExpect(status().isOk());
    }

    private String buildExpiredAccessToken() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .subject("1")
            .claim("type", "access")
            .claim("role", UserRole.USER.name())
            .issuedAt(Date.from(Instant.now().minusSeconds(120)))
            .expiration(Date.from(Instant.now().minusSeconds(60)))
            .signWith(key)
            .compact();
    }
}
