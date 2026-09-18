package com.pharmaprice.admin.service;

import com.pharmaprice.admin.dto.AdminDrugStatsResponse;
import com.pharmaprice.admin.dto.AdminPriceGapResponse;
import com.pharmaprice.admin.dto.AdminRegionStatsResponse;
import com.pharmaprice.admin.dto.AdminStatsOverviewResponse;
import com.pharmaprice.admin.exception.DrugNotFoundException;
import com.pharmaprice.admin.repository.AdminStatsQueryRepository;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.recommendation.repository.PriceStatRepository;
import com.pharmaprice.report.repository.PriceReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link AdminStatsService} 구현. 각 테이블의 총 건수는 새 쿼리 없이 기존
 * {@code JpaRepository.count()}를 그대로 조합한다 — {@link AdminStatsQueryRepository}는
 * {@code count()}로 표현할 수 없는 집계(추이, 지역별, 히스토그램, 가격 격차)만 담당한다.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

    /** {@code docs/API.md} §8 price-gaps "최대 50". {@code SearchServiceImpl.MAX_LIMIT}과 동일한 clamp 관례. */
    private static final int MAX_PRICE_GAP_LIMIT = 50;

    private final PharmacyRepository pharmacyRepository;
    private final DrugRepository drugRepository;
    private final PriceReportRepository priceReportRepository;
    private final AppUserRepository appUserRepository;
    private final PriceStatRepository priceStatRepository;
    private final AdminStatsQueryRepository adminStatsQueryRepository;

    @Override
    public AdminStatsOverviewResponse overview() {
        AdminStatsOverviewResponse.Totals totals = new AdminStatsOverviewResponse.Totals(
            pharmacyRepository.count(), drugRepository.count(), priceReportRepository.count(),
            appUserRepository.count(), priceStatRepository.count());

        var recentTrend = adminStatsQueryRepository.recentReportTrend().stream()
            .map(row -> new AdminStatsOverviewResponse.TrendPoint(row.getReportDate(), row.getReportCount()))
            .toList();

        long flaggedReportCount = priceReportRepository.countByFlaggedTrue();

        long denominator = totals.pharmacyCount() * totals.drugCount();
        double coverageRate = denominator == 0 ? 0.0 : (double) totals.coveredPairCount() / denominator;

        return new AdminStatsOverviewResponse(totals, recentTrend, flaggedReportCount, coverageRate);
    }

    @Override
    public AdminRegionStatsResponse regions(String regionCode, Long drugId, String sido) {
        var rows = adminStatsQueryRepository.regionStats(regionCode, drugId, sido).stream()
            .map(row -> new AdminRegionStatsResponse.Row(
                new AdminRegionStatsResponse.RegionInfo(row.getRegionCode(), row.getSido(), row.getSigungu()),
                new AdminRegionStatsResponse.DrugInfo(row.getDrugId(), row.getDrugDisplayName()),
                row.getAvgPrice(), row.getMinPrice(), row.getMaxPrice(),
                row.getPharmacyCount(), row.getReportCount() == null ? 0L : row.getReportCount()))
            .toList();
        return new AdminRegionStatsResponse(rows);
    }

    @Override
    public AdminDrugStatsResponse drugStats(Long drugId) {
        Drug drug = drugRepository.findById(drugId)
            .orElseThrow(() -> new DrugNotFoundException("drug not found: " + drugId));

        var distribution = adminStatsQueryRepository.drugHistogram(drugId).stream()
            .map(row -> new AdminDrugStatsResponse.HistogramBucket(row.getBucketFrom(), row.getBucketTo(), row.getBucketCount()))
            .toList();

        var byRegion = adminStatsQueryRepository.drugByRegion(drugId).stream()
            .map(row -> new AdminDrugStatsResponse.RegionAvg(row.getSido(), row.getSigungu(), row.getAvgPrice(), row.getPharmacyCount()))
            .toList();

        var nationalRow = adminStatsQueryRepository.drugNational(drugId);
        var national = new AdminDrugStatsResponse.National(
            nationalRow.getAvg(), nationalRow.getMedian(), nationalRow.getMin(), nationalRow.getMax(), nationalRow.getStdDev());

        var drugInfo = new AdminDrugStatsResponse.DrugInfo(drug.getId(), drug.getDisplayName(), drug.getPackageUnit());
        return new AdminDrugStatsResponse(drugInfo, distribution, byRegion, national);
    }

    @Override
    public AdminPriceGapResponse priceGaps(int limit) {
        int clampedLimit = Math.min(Math.max(limit, 1), MAX_PRICE_GAP_LIMIT);
        var rows = adminStatsQueryRepository.priceGaps(clampedLimit).stream()
            .map(row -> new AdminPriceGapResponse.Row(
                new AdminPriceGapResponse.DrugInfo(row.getDrugId(), row.getDrugDisplayName()),
                new AdminPriceGapResponse.RegionPrice(row.getCheapestSido(), row.getCheapestSigungu(), row.getCheapestAvg()),
                new AdminPriceGapResponse.RegionPrice(row.getPriciestSido(), row.getPriciestSigungu(), row.getPriciestAvg()),
                row.getGap(), row.getGapPct()))
            .toList();
        return new AdminPriceGapResponse(rows);
    }
}
