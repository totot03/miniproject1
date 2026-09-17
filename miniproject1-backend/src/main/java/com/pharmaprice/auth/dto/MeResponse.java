package com.pharmaprice.auth.dto;

import com.pharmaprice.auth.domain.UserRole;
import java.time.OffsetDateTime;

/**
 * {@code docs/API.md} §2 {@code GET /auth/me} 응답.
 */
public record MeResponse(
    Long id,
    String email,
    String nickname,
    UserRole role,
    int reportCount,
    OffsetDateTime createdAt
) {
}
