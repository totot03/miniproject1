package com.pharmaprice.recommendation.repository;

import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * T-06 은 골격과 기본 조회 메서드만 담당한다. IQR·중앙값 재계산 native
 * 쿼리와 {@code ON CONFLICT} upsert 는 T-10 에서 이 인터페이스에
 * 덧붙인다 — 새 리포지토리를 만들지 않는다.
 */
public interface PriceStatRepository extends JpaRepository<PharmacyDrugPriceStat, Long> {

    /** T-10 재계산 서비스가 (약국, 약품) 조합의 기존 통계 행을 찾을 때 쓴다. */
    Optional<PharmacyDrugPriceStat> findByPharmacyIdAndDrugId(Long pharmacyId, Long drugId);
}
