package com.pharmaprice.admin.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code docs/API.md} §8 {@code GET /api/v1/admin/stats/overview} 응답.
 *
 * <p>{@code coverageRate}는 (약국×약품) 조합 중 가격 정보가 있는 비율이다
 * ({@code totals.coveredPairCount / (totals.pharmacyCount * totals.drugCount)}).</p>
 */
public record AdminStatsOverviewResponse(
    Totals totals,
    List<TrendPoint> recentTrend,
    long flaggedReportCount,
    double coverageRate
) {

    public record Totals(long pharmacyCount, long drugCount, long reportCount, long userCount, long coveredPairCount) {
    }

    /** 제보가 없는 날짜는 목록에서 빠진다(0건 채움 없음) — API.md가 요구하지 않는 한 과설계를 피한다. */
    public record TrendPoint(LocalDate date, long reportCount) {
    }
}
