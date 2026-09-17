package com.pharmaprice.auth.security;

import com.pharmaprice.auth.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 서명·발급·검증 유틸. HS256 고정.
 *
 * <p>refresh 토큰을 {@code refresh_token} 테이블에 저장하는 것은 이 클래스의
 * 책임이 아니다 — 로그인·재발급 트랜잭션이 있는 T-24 {@code AuthService} 가
 * {@link #hashRefreshToken(String)} 결과를 저장한다.</p>
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtTokenProvider(JwtProperties properties) {
        // secret 이 32바이트(HS256 최소 256비트) 미만이면 Keys.hmacShaKeyFor 가
        // WeakKeyException 을 던진다 — 기동 시점에 바로 실패하는 게 의도된 동작이다.
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(properties.accessTokenTtlMinutes());
        this.refreshTokenTtl = Duration.ofDays(properties.refreshTokenTtlDays());
    }

    public String generateAccessToken(Long userId, UserRole role) {
        return buildToken(userId, TYPE_ACCESS, accessTokenTtl, role);
    }

    public String generateRefreshToken(Long userId) {
        return buildToken(userId, TYPE_REFRESH, refreshTokenTtl, null);
    }

    private String buildToken(Long userId, String type, Duration ttl, UserRole role) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
            .subject(userId.toString())
            .claim(CLAIM_TYPE, type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key);
        if (role != null) {
            builder.claim(CLAIM_ROLE, role.name());
        }
        return builder.compact();
    }

    /**
     * access 토큰을 검증하고 principal 을 복원한다. 서명 불일치, 만료,
     * 형식 오류, refresh 토큰 오용 등 모든 실패는 {@link JwtException} 으로
     * 던진다 — 호출자({@code JwtAuthenticationFilter})가 잡아서 무시한다.
     */
    public AuthPrincipal validateAndGetPrincipal(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("access 토큰이 아닙니다.");
        }

        Long userId = Long.valueOf(claims.getSubject());
        UserRole role = UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
        return new AuthPrincipal(userId, role);
    }

    /** refresh 토큰 원문의 SHA-256 해시(hex, 64자)를 반환한다. 원문은 저장하지 않는다. */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
