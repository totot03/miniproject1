package com.pharmaprice.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.auth.repository.RefreshTokenRepository;
import com.pharmaprice.support.TestFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * app_user / refresh_token 저장·조회, report_count 증감,
 * refresh_token 의 유효성 판단을 검증한다.
 */
class AuthEntityMappingTest extends AbstractIntegrationTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void appUserIsSavedWithDefaultRoleAndStatus() {
        appUserRepository.save(TestFixtures.appUser("a@b.c"));
        entityManager.flush();
        entityManager.clear();

        AppUser found = appUserRepository.findByEmail("a@b.c").orElseThrow();
        assertThat(found.getRole()).isEqualTo(UserRole.USER);
        assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(found.getReportCount()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void existsByEmailDetectsDuplicate() {
        appUserRepository.save(TestFixtures.appUser("dup@b.c"));
        entityManager.flush();

        assertThat(appUserRepository.existsByEmail("dup@b.c")).isTrue();
        assertThat(appUserRepository.existsByEmail("nobody@b.c")).isFalse();
    }

    @Test
    void reportCountIncreasesAndDecreases() {
        AppUser user = appUserRepository.save(TestFixtures.appUser("b@c.d"));
        entityManager.flush();

        user.increaseReportCount();
        user.increaseReportCount();
        entityManager.flush();
        entityManager.clear();

        AppUser afterIncrease = appUserRepository.findById(user.getId()).orElseThrow();
        assertThat(afterIncrease.getReportCount()).isEqualTo(2);

        afterIncrease.decreaseReportCount();
        entityManager.flush();
        entityManager.clear();

        AppUser afterDecrease = appUserRepository.findById(user.getId()).orElseThrow();
        assertThat(afterDecrease.getReportCount()).isEqualTo(1);
    }

    @Test
    void refreshTokenIsSavedAndFoundByTokenHash() {
        AppUser user = appUserRepository.save(TestFixtures.appUser("c@d.e"));

        refreshTokenRepository.save(
            RefreshToken.builder()
                .user(user)
                .tokenHash("hash-abc-123")
                .expiresAt(OffsetDateTime.now().plusDays(14))
                .build());

        entityManager.flush();
        entityManager.clear();

        RefreshToken found = refreshTokenRepository.findByTokenHash("hash-abc-123").orElseThrow();
        assertThat(found.getUser().getEmail()).isEqualTo("c@d.e");
        assertThat(found.getRevokedAt()).isNull();
        assertThat(found.isValid(OffsetDateTime.now())).isTrue();
    }

    @Test
    void revokedRefreshTokenIsNoLongerValid() {
        AppUser user = appUserRepository.save(TestFixtures.appUser("d@e.f"));
        RefreshToken token = refreshTokenRepository.save(
            RefreshToken.builder()
                .user(user)
                .tokenHash("hash-def-456")
                .expiresAt(OffsetDateTime.now().plusDays(14))
                .build());

        token.revoke(OffsetDateTime.now());
        entityManager.flush();
        entityManager.clear();

        RefreshToken found = refreshTokenRepository.findByTokenHash("hash-def-456").orElseThrow();
        assertThat(found.getRevokedAt()).isNotNull();
        assertThat(found.isValid(OffsetDateTime.now())).isFalse();
    }
}
