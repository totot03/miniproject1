package com.pharmaprice.admin.dto;

import java.util.List;

/**
 * {@code docs/API.md} §8 {@code GET /api/v1/admin/stats/drugs/{drugId}} 응답.
 * 대상 약품에 {@code pharmacy_drug_price_stat} 행이 하나도 없으면 {@code distribution}·
 * {@code byRegion}은 빈 리스트, {@code national}은 전 필드 {@code null}이다.
 */
public record AdminDrugStatsResponse(DrugInfo drug, List<HistogramBucket> distribution, List<RegionAvg> byRegion, National national) {

    public record DrugInfo(Long id, String displayName, String packageUnit) {
    }

    public record HistogramBucket(int bucketFrom, int bucketTo, int count) {
    }

    public record RegionAvg(String sido, String sigungu, Integer avgPrice, long pharmacyCount) {
    }

    public record National(Integer avg, Integer median, Integer min, Integer max, Integer stdDev) {
    }
}
