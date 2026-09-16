package com.pharmaprice.recommendation.service;

import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import java.util.Optional;

/**
 * (약국, 약품) 조합의 대표가격 통계를 재계산한다. {@code docs/PRD.md} §F3.1 절차
 * (90일 창 → 0건이면 180일로 확대 → 4건 이상이면 IQR 이상치 제거 → 중앙값)를 구현한다.
 */
public interface PriceStatService {

    /**
     * 해당 조합의 통계를 재계산해 {@code pharmacy_drug_price_stat} 에 upsert하고,
     * 유효 제보가 0이면 그 행을 삭제한다. 호출 지점: 제보 생성 직후(T-26),
     * 관리자 상태 변경(T-32).
     *
     * <p>반환된 엔티티의 {@code pharmacy}/{@code drug} 연관관계는 {@code LAZY}다 —
     * 이 메서드가 연 트랜잭션 밖에서 역참조하면 {@code LazyInitializationException}이 난다.</p>
     *
     * @return 유효 제보가 있어 통계가 갱신됐으면 그 결과, 유효 제보가 0이라 행을 삭제했으면 빈 값
     */
    Optional<PharmacyDrugPriceStat> recalculate(long pharmacyId, long drugId);
}
