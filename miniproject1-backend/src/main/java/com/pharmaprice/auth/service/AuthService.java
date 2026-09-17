package com.pharmaprice.auth.service;

import com.pharmaprice.auth.dto.LoginRequest;
import com.pharmaprice.auth.dto.LoginResponse;
import com.pharmaprice.auth.dto.LogoutRequest;
import com.pharmaprice.auth.dto.MeResponse;
import com.pharmaprice.auth.dto.RefreshRequest;
import com.pharmaprice.auth.dto.SignupRequest;
import com.pharmaprice.auth.dto.SignupResponse;

/**
 * {@code docs/API.md} §2 인증 5개 엔드포인트의 비즈니스 로직.
 */
public interface AuthService {

    SignupResponse signup(SignupRequest request);

    LoginResponse login(LoginRequest request);

    LoginResponse refresh(RefreshRequest request);

    void logout(LogoutRequest request);

    MeResponse me(Long userId);
}
