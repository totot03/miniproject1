package com.pharmaprice.auth.service;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.RefreshToken;
import com.pharmaprice.auth.dto.LoginRequest;
import com.pharmaprice.auth.dto.LoginResponse;
import com.pharmaprice.auth.dto.LogoutRequest;
import com.pharmaprice.auth.dto.MeResponse;
import com.pharmaprice.auth.dto.RefreshRequest;
import com.pharmaprice.auth.dto.SignupRequest;
import com.pharmaprice.auth.dto.SignupResponse;
import com.pharmaprice.auth.dto.UserSummary;
import com.pharmaprice.auth.exception.AuthenticationFailedException;
import com.pharmaprice.auth.exception.EmailAlreadyExistsException;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.auth.repository.RefreshTokenRepository;
import com.pharmaprice.auth.security.JwtProperties;
import com.pharmaprice.auth.security.JwtTokenProvider;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code docs/ROADMAP.md} T-24 구현체.
 *
 * <p>{@link #login}과 {@link #refresh}의 실패는 항상 동일한
 * {@link AuthenticationFailedException}(같은 메시지)만 던진다 — 이메일 미존재와
 * 비밀번호 불일치를 코드 경로로도 구분하지 않아 계정 존재 여부를 노출하지
 * 않는다({@code docs/ROADMAP.md} T-24 요구사항).</p>
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String AUTH_FAILED_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (appUserRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("이미 사용 중인 이메일입니다.");
        }

        AppUser user = AppUser.builder()
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .nickname(request.nickname())
            .build();
        AppUser saved = appUserRepository.save(user);

        return new SignupResponse(saved.getId(), saved.getEmail(), saved.getNickname(), saved.getRole(), saved.getCreatedAt());
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmail(request.email())
            .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
            .orElseThrow(() -> new AuthenticationFailedException(AUTH_FAILED_MESSAGE));

        return issueTokenResponse(user);
    }

    /** revoke와 재발급을 하나의 트랜잭션으로 묶어야 rotation의 원자성이 보장된다. */
    @Override
    @Transactional
    public LoginResponse refresh(RefreshRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        String tokenHash = jwtTokenProvider.hashRefreshToken(request.refreshToken());
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
            .filter(t -> t.isValid(now))
            .orElseThrow(() -> new AuthenticationFailedException(AUTH_FAILED_MESSAGE));

        token.revoke(now);
        return issueTokenResponse(token.getUser());
    }

    /** 토큰이 없거나 이미 revoke됐어도 예외를 던지지 않는다 — 로그아웃은 멱등해야 한다. */
    @Override
    @Transactional
    public void logout(LogoutRequest request) {
        String tokenHash = jwtTokenProvider.hashRefreshToken(request.refreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash)
            .ifPresent(token -> token.revoke(OffsetDateTime.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        // 여기 도달했다는 것은 필터 체인이 유효한 access 토큰을 이미 검증했다는 뜻이므로
        // 사용자가 없을 경우를 별도로 처리하지 않는다.
        AppUser user = appUserRepository.findById(userId).orElseThrow();
        return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole(), user.getReportCount(), user.getCreatedAt());
    }

    private LoginResponse issueTokenResponse(AppUser user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
        String refreshTokenRaw = jwtTokenProvider.generateRefreshToken(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
            .user(user)
            .tokenHash(jwtTokenProvider.hashRefreshToken(refreshTokenRaw))
            .expiresAt(OffsetDateTime.now().plusDays(jwtProperties.refreshTokenTtlDays()))
            .build();
        refreshTokenRepository.save(refreshToken);

        long expiresIn = jwtProperties.accessTokenTtlMinutes() * 60L;
        UserSummary userSummary = new UserSummary(user.getId(), user.getNickname(), user.getRole());
        return new LoginResponse(accessToken, refreshTokenRaw, expiresIn, userSummary);
    }
}
