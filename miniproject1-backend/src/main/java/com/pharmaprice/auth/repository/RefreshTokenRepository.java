package com.pharmaprice.auth.repository;

import com.pharmaprice.auth.domain.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** T-23 토큰 갱신·회전에서 원문 해시로 발급 이력을 찾는다. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
