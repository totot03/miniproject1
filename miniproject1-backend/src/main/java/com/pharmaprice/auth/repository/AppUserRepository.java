package com.pharmaprice.auth.repository;

import com.pharmaprice.auth.domain.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** T-24 로그인에서 이메일로 사용자를 찾는다. */
    Optional<AppUser> findByEmail(String email);

    /** T-24 회원가입에서 이메일 중복을 확인한다. */
    boolean existsByEmail(String email);
}
