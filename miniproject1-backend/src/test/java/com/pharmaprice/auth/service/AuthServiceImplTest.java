package com.pharmaprice.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.RefreshToken;
import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.auth.dto.LoginRequest;
import com.pharmaprice.auth.dto.LoginResponse;
import com.pharmaprice.auth.dto.LogoutRequest;
import com.pharmaprice.auth.dto.MeResponse;
import com.pharmaprice.auth.dto.RefreshRequest;
import com.pharmaprice.auth.dto.SignupRequest;
import com.pharmaprice.auth.dto.SignupResponse;
import com.pharmaprice.auth.exception.AuthenticationFailedException;
import com.pharmaprice.auth.exception.EmailAlreadyExistsException;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.auth.repository.RefreshTokenRepository;
import com.pharmaprice.auth.security.JwtProperties;
import com.pharmaprice.auth.security.JwtTokenProvider;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@code docs/ROADMAP.md} T-24 {@link AuthServiceImpl} 비즈니스 로직 검증.
 * 리포지토리·{@link JwtTokenProvider}·{@link PasswordEncoder}는 모두 Mockito 목이다.
 */
class AuthServiceImplTest {

    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final JwtProperties jwtProperties = new JwtProperties("unused-in-unit-test", 30, 14);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(appUserRepository, refreshTokenRepository, jwtTokenProvider, jwtProperties, passwordEncoder);
    }

    private AppUser user(long id, String email, UserRole role) {
        return AppUser.builder().id(id).email(email).passwordHash("HASHED").nickname("닉네임").role(role).build();
    }

    private RefreshToken refreshToken(AppUser owner, String tokenHash, OffsetDateTime expiresAt) {
        return RefreshToken.builder().user(owner).tokenHash(tokenHash).expiresAt(expiresAt).build();
    }

    @Test
    void signup_중복이메일이면_예외를던지고저장하지않는다() {
        given(appUserRepository.existsByEmail("dup@test.com")).willReturn(true);

        assertThatThrownBy(() -> service.signup(new SignupRequest("dup@test.com", "Password123", "닉네임")))
            .isInstanceOf(EmailAlreadyExistsException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void signup_성공하면_비밀번호를해시해서저장한다() {
        given(appUserRepository.existsByEmail("new@test.com")).willReturn(false);
        given(passwordEncoder.encode("Password123")).willReturn("HASHED");
        given(appUserRepository.save(any(AppUser.class)))
            .willReturn(AppUser.builder().id(1L).email("new@test.com").passwordHash("HASHED").nickname("닉네임").build());

        SignupResponse response = service.signup(new SignupRequest("new@test.com", "Password123", "닉네임"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("new@test.com");
        assertThat(response.role()).isEqualTo(UserRole.USER);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("HASHED");
    }

    @Test
    void login_존재하지않는이메일이면_인증실패예외() {
        given(appUserRepository.findByEmail("nobody@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@test.com", "whatever")))
            .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void login_이메일없음과비밀번호불일치는_동일한예외와메시지를던진다() {
        AppUser user = user(1L, "user@test.com", UserRole.USER);
        given(appUserRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "HASHED")).willReturn(false);
        given(appUserRepository.findByEmail("nobody@test.com")).willReturn(Optional.empty());

        AuthenticationFailedException byWrongPassword = catchThrowableOfType(
            () -> service.login(new LoginRequest("user@test.com", "wrong")), AuthenticationFailedException.class);
        AuthenticationFailedException byUnknownEmail = catchThrowableOfType(
            () -> service.login(new LoginRequest("nobody@test.com", "whatever")), AuthenticationFailedException.class);

        assertThat(byWrongPassword).isNotNull();
        assertThat(byUnknownEmail).isNotNull();
        assertThat(byWrongPassword.getMessage()).isEqualTo(byUnknownEmail.getMessage());
    }

    @Test
    void login_성공하면_토큰을발급하고refreshToken을저장한다() {
        AppUser user = user(7L, "ok@test.com", UserRole.USER);
        given(appUserRepository.findByEmail("ok@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("correct", "HASHED")).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(7L, UserRole.USER)).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(7L)).willReturn("refresh-token-raw");
        given(jwtTokenProvider.hashRefreshToken("refresh-token-raw")).willReturn("hash-abc");

        LoginResponse response = service.login(new LoginRequest("ok@test.com", "correct"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-raw");
        assertThat(response.expiresIn()).isEqualTo(30 * 60L);
        assertThat(response.user().id()).isEqualTo(7L);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("hash-abc");
    }

    @Test
    void refresh_유효한토큰이면_기존토큰을revoke하고새토큰을발급한다() {
        AppUser user = user(3L, "r@test.com", UserRole.USER);
        RefreshToken existing = refreshToken(user, "old-hash", OffsetDateTime.now().plusDays(1));
        given(jwtTokenProvider.hashRefreshToken("raw-old")).willReturn("old-hash");
        given(refreshTokenRepository.findByTokenHash("old-hash")).willReturn(Optional.of(existing));
        given(jwtTokenProvider.generateAccessToken(3L, UserRole.USER)).willReturn("new-access");
        given(jwtTokenProvider.generateRefreshToken(3L)).willReturn("new-refresh-raw");
        given(jwtTokenProvider.hashRefreshToken("new-refresh-raw")).willReturn("new-hash");

        LoginResponse response = service.refresh(new RefreshRequest("raw-old"));

        assertThat(existing.getRevokedAt()).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-raw");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refresh_이미revoke된토큰이면_인증실패예외() {
        AppUser user = user(4L, "revoked@test.com", UserRole.USER);
        RefreshToken revoked = refreshToken(user, "hash-revoked", OffsetDateTime.now().plusDays(1));
        revoked.revoke(OffsetDateTime.now().minusMinutes(1));
        given(jwtTokenProvider.hashRefreshToken("raw-revoked")).willReturn("hash-revoked");
        given(refreshTokenRepository.findByTokenHash("hash-revoked")).willReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("raw-revoked")))
            .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void refresh_만료된토큰이면_인증실패예외() {
        AppUser user = user(5L, "expired@test.com", UserRole.USER);
        RefreshToken expired = refreshToken(user, "hash-expired", OffsetDateTime.now().minusSeconds(1));
        given(jwtTokenProvider.hashRefreshToken("raw-expired")).willReturn("hash-expired");
        given(refreshTokenRepository.findByTokenHash("hash-expired")).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("raw-expired")))
            .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void refresh_존재하지않는토큰이면_인증실패예외() {
        given(jwtTokenProvider.hashRefreshToken("raw-none")).willReturn("hash-none");
        given(refreshTokenRepository.findByTokenHash("hash-none")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("raw-none")))
            .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void logout_토큰이있으면_revoke한다() {
        AppUser user = user(6L, "logout@test.com", UserRole.USER);
        RefreshToken token = refreshToken(user, "hash-logout", OffsetDateTime.now().plusDays(1));
        given(jwtTokenProvider.hashRefreshToken("raw-logout")).willReturn("hash-logout");
        given(refreshTokenRepository.findByTokenHash("hash-logout")).willReturn(Optional.of(token));

        service.logout(new LogoutRequest("raw-logout"));

        assertThat(token.getRevokedAt()).isNotNull();
    }

    @Test
    void logout_토큰이없어도_예외없이반환한다() {
        given(jwtTokenProvider.hashRefreshToken("raw-missing")).willReturn("hash-missing");
        given(refreshTokenRepository.findByTokenHash("hash-missing")).willReturn(Optional.empty());

        assertThatCode(() -> service.logout(new LogoutRequest("raw-missing"))).doesNotThrowAnyException();
    }

    @Test
    void logout_이미revoke된토큰이어도_예외없이다시반환한다() {
        AppUser user = user(8L, "already@test.com", UserRole.USER);
        RefreshToken token = refreshToken(user, "hash-already", OffsetDateTime.now().plusDays(1));
        token.revoke(OffsetDateTime.now().minusMinutes(5));
        given(jwtTokenProvider.hashRefreshToken("raw-already")).willReturn("hash-already");
        given(refreshTokenRepository.findByTokenHash("hash-already")).willReturn(Optional.of(token));

        assertThatCode(() -> service.logout(new LogoutRequest("raw-already"))).doesNotThrowAnyException();
    }

    @Test
    void me_사용자정보와reportCount를그대로반환한다() {
        AppUser user = user(9L, "me@test.com", UserRole.ADMIN);
        given(appUserRepository.findById(9L)).willReturn(Optional.of(user));

        MeResponse response = service.me(9L);

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.email()).isEqualTo("me@test.com");
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
        assertThat(response.reportCount()).isEqualTo(user.getReportCount());
    }
}
