package com.pharmaprice.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code docs/API.md} §2 {@code POST /auth/login} 요청.
 *
 * <p>이메일 형식 검증을 걸지 않는다 — 형식이 틀려도 존재하지 않는 계정과
 * 동일하게 {@code 401 UNAUTHENTICATED} 로 처리해야 하므로, 별도의
 * {@code 400 VALIDATION_FAILED} 분기를 만들지 않는다.</p>
 */
public record LoginRequest(
    @NotBlank String email,
    @NotBlank String password
) {
}
