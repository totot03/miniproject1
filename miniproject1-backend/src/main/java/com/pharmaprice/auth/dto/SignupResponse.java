package com.pharmaprice.auth.dto;

import com.pharmaprice.auth.domain.UserRole;
import java.time.OffsetDateTime;

/**
 * {@code docs/API.md} §2 {@code POST /auth/signup} 응답. {@code AppUser} 엔티티를
 * 그대로 반환하지 않기 위한 분리 — {@code passwordHash} 는 담지 않는다.
 */
public record SignupResponse(
    Long id,
    String email,
    String nickname,
    UserRole role,
    OffsetDateTime createdAt
) {
}
