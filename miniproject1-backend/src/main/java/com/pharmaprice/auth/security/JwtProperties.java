package com.pharmaprice.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.jwt.* 설정을 바인딩한다.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    int accessTokenTtlMinutes,
    int refreshTokenTtlDays
) {
}
