package com.pharmaprice.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code docs/API.md} §2 {@code POST /auth/logout} 요청.
 */
public record LogoutRequest(
    @NotBlank String refreshToken
) {
}
