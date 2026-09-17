package com.pharmaprice.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code docs/API.md} §2 {@code POST /auth/signup} 요청.
 */
public record SignupRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 64) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$") String password,
    @NotBlank @Size(min = 2, max = 30) String nickname
) {
}
