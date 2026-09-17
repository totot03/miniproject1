package com.pharmaprice.auth.dto;

/**
 * {@code docs/API.md} §2 {@code POST /auth/login} 응답. {@code POST /auth/refresh}
 * 도 "login과 동일한 형태"로 이 타입을 그대로 재사용한다.
 *
 * @param expiresIn accessToken 만료까지 남은 초. {@code JwtProperties.accessTokenTtlMinutes() * 60}
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    UserSummary user
) {
}
