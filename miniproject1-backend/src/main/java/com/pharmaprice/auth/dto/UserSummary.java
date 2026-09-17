package com.pharmaprice.auth.dto;

import com.pharmaprice.auth.domain.UserRole;

/**
 * {@link LoginResponse#user()} 필드. {@code docs/API.md} §2 login/refresh 응답 공통.
 */
public record UserSummary(
    Long id,
    String nickname,
    UserRole role
) {
}
