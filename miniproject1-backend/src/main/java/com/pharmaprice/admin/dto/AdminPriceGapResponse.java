package com.pharmaprice.admin.dto;

import java.util.List;

/**
 * {@code docs/API.md} §8 {@code GET /api/v1/admin/stats/price-gaps} 응답.
 * {@code docs/DATABASE.md} §5.4 기준 표본 3건 미만인 지역·약품은 이미 제외된 결과다
 * ({@code AdminStatsQueryRepository.priceGaps} 참고).
 */
public record AdminPriceGapResponse(List<Row> rows) {

    public record Row(DrugInfo drug, RegionPrice cheapestRegion, RegionPrice priciestRegion, int gap, double gapPct) {
    }

    public record DrugInfo(Long id, String displayName) {
    }

    public record RegionPrice(String sido, String sigungu, int avgPrice) {
    }
}
