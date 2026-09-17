package com.pharmaprice.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pharmaprice.auth.domain.UserRole;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-jwt-secret-key-for-unit-tests-only-32-bytes-min";

    private final JwtTokenProvider provider =
        new JwtTokenProvider(new JwtProperties(SECRET, 30, 14));

    @Test
    void generatesAccessTokenWithSubjectAndRoleClaim() {
        String token = provider.generateAccessToken(42L, UserRole.ADMIN);

        AuthPrincipal principal = provider.validateAndGetPrincipal(token);

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        String refreshToken = provider.generateRefreshToken(1L);

        assertThatThrownBy(() -> provider.validateAndGetPrincipal(refreshToken))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenFailsValidation() {
        String expiredToken = buildTokenWithExpiry(SECRET, Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> provider.validateAndGetPrincipal(expiredToken))
            .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedSignatureFailsValidation() {
        String otherSecret = "another-32-byte-plus-secret-for-signature-mismatch-test";
        String tokenSignedWithOtherKey = buildTokenWithExpiry(otherSecret, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> provider.validateAndGetPrincipal(tokenSignedWithOtherKey))
            .isInstanceOf(SignatureException.class);
    }

    @Test
    void hashRefreshTokenIsDeterministicAndHex64() {
        String hash1 = provider.hashRefreshToken("raw-refresh-token-value");
        String hash2 = provider.hashRefreshToken("raw-refresh-token-value");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
        assertThat(hash1).matches("[0-9a-f]{64}");
    }

    @Test
    void secretShorterThan32BytesFailsFastAtConstruction() {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("too-short-secret", 30, 14)))
            .isInstanceOf(WeakKeyException.class);
    }

    private String buildTokenWithExpiry(String secret, Instant expiry) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .subject("1")
            .claim("type", "access")
            .claim("role", UserRole.USER.name())
            .issuedAt(Date.from(Instant.now().minusSeconds(120)))
            .expiration(Date.from(expiry))
            .signWith(key)
            .compact();
    }
}
