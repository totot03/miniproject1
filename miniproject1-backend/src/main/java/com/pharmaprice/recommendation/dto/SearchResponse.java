package com.pharmaprice.recommendation.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code docs/API.md} §5 {@code GET /api/v1/search} 응답. 필드명·구조는 API.md
 * 예시 JSON과 정확히 일치시킨다(camelCase). {@code suggestion}은 결과가 0건일
 * 때만 채워지고, 그 외에는 {@code null}이다.
 */
public record SearchResponse(
    DrugInfo drug,
    QueryInfo query,
    SearchSummary summary,
    DataSource dataSource,
    List<SearchResultItem> results,
    SearchSuggestion suggestion
) {

    public record DrugInfo(Long id, String displayName, String packageUnit, String imageUrl) {
    }

    /** @param locationSource {@code "GPS"} \| {@code "REGION"} */
    public record QueryInfo(double lat, double lng, int radius, String sort, String locationSource) {
    }

    /**
     * 결과 0건이면 가격 필드는 전부 {@code null}이다(집계 대상 자체가 없다는 뜻).
     * 가격 필드는 **limit 적용 전** 후보군 전체 기준이고, {@code resultCount}만
     * limit 적용 후 실제 반환 건수다.
     */
    public record SearchSummary(
        int resultCount, Integer candidateAvgPrice, Integer candidateMinPrice, Integer candidateMaxPrice,
        Integer maxSaving
    ) {
    }

    /**
     * @param recommended  SCORE 기준 1위인지 여부. {@code sort} 파라미터를 바꿔도 값이 유지된다.
     * @param distanceM    미터 단위로 반올림한 거리
     * @param score        SCORE 계산값(0~1, 소수점 4자리). {@code sort}와 무관하게 항상 채워진다.
     */
    public record SearchResultItem(
        int rank, boolean recommended, PharmacyInfo pharmacy, PriceInfo price,
        long distanceM, double score, ScoreBreakdown scoreBreakdown, List<Badge> badges
    ) {
    }

    public record PharmacyInfo(Long id, String name, String addressRoad, double lat, double lng, String phone) {
    }

    /** @param savingVsCandidateAvg {@code candidateAvgPrice - repPrice} (양수면 후보 평균보다 저렴) */
    public record PriceInfo(
        int repPrice, int minPrice, int avgPrice, int savingVsCandidateAvg,
        int reportCount, LocalDate lastReportedAt, long daysSinceLastReport
    ) {
    }

    /** @param type 현재는 {@code "EXPAND_RADIUS"}만 존재 */
    public record SearchSuggestion(String type, int recommendedRadius, long estimatedCount) {
    }
}
