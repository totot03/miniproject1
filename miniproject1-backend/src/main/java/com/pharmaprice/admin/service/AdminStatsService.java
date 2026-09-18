package com.pharmaprice.admin.service;

import com.pharmaprice.admin.dto.AdminDrugStatsResponse;
import com.pharmaprice.admin.dto.AdminPriceGapResponse;
import com.pharmaprice.admin.dto.AdminRegionStatsResponse;
import com.pharmaprice.admin.dto.AdminStatsOverviewResponse;

/**
 * {@code docs/ROADMAP.md} T-31 / {@code docs/API.md} §8. 관리자 전용 통계 4종을 조회한다.
 */
public interface AdminStatsService {

    AdminStatsOverviewResponse overview();

    /**
     * @param regionCode 필터. {@code null}이면 전체
     * @param drugId     필터. {@code null}이면 전체
     * @param sido       필터. {@code null}이면 전체
     */
    AdminRegionStatsResponse regions(String regionCode, Long drugId, String sido);

    /** @throws com.pharmaprice.admin.exception.DrugNotFoundException drugId가 존재하지 않으면 */
    AdminDrugStatsResponse drugStats(Long drugId);

    /** @param limit 1~50으로 clamp된다 */
    AdminPriceGapResponse priceGaps(int limit);
}
