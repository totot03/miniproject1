package com.pharmaprice.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code docs/API.md} §2 {@code POST /auth/refresh} 요청.
 */
public record RefreshRequest(
    @NotBlank String refreshToken
) {
}
