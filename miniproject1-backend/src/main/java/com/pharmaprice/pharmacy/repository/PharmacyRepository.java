package com.pharmaprice.pharmacy.repository;

import com.pharmaprice.pharmacy.domain.Pharmacy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {

    /** T-07 시드 변환 스크립트가 {@code ON CONFLICT} 대신 애플리케이션에서
     * 멱등성을 확인할 때 쓴다. */
    Optional<Pharmacy> findByHiraCode(String hiraCode);
}
