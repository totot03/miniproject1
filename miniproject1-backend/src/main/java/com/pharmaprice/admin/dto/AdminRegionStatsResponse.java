package com.pharmaprice.admin.dto;

import java.util.List;

/**
 * {@code docs/API.md} §8 {@code GET /api/v1/admin/stats/regions} 응답.
 * {@code docs/DATABASE.md} §5.3 쿼리 결과를 그대로 옮긴다.
 */
public record AdminRegionStatsResponse(List<Row> rows) {

    public record Row(
        RegionInfo region, DrugInfo drug,
        Integer avgPrice, Integer minPrice, Integer maxPrice,
        long pharmacyCount, long reportCount
    ) {
    }

    public record RegionInfo(String code, String sido, String sigungu) {
    }

    public record DrugInfo(Long id, String displayName) {
    }
}
