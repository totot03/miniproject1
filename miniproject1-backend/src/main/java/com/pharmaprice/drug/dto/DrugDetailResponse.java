package com.pharmaprice.drug.dto;

/**
 * {@code docs/API.md} §3 {@code GET /api/v1/drugs/{drugId}} 상세 응답 필드.
 */
public record DrugDetailResponse(
    Long id,
    String itemSeq,
    String displayName,
    String name,
    String maker,
    String category,
    String form,
    String packageUnit,
    String imageUrl,
    PriceStats priceStats
) {

    /**
     * 전국 가격 통계. {@code pharmacy_drug_price_stat}을 {@code drug_id}로 집계한 값이라,
     * 해당 약품에 통계가 하나도 없으면 {@code nationalAvg}/{@code Min}/{@code Max}는
     * {@code null}이고 {@code pharmacyCount}/{@code reportCount}는 0이다.
     */
    public record PriceStats(
        Integer nationalAvg,
        Integer nationalMin,
        Integer nationalMax,
        long pharmacyCount,
        long reportCount
    ) {
    }
}
