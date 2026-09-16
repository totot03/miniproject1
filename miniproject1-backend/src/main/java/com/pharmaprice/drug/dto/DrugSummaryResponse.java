package com.pharmaprice.drug.dto;

/**
 * {@code docs/API.md} §3 {@code GET /api/v1/drugs} 목록 응답 필드.
 * 필드명·순서는 API.md 예시 JSON과 정확히 일치시킨다(camelCase).
 *
 * @param nationalAvgPrice 전국 {@code pharmacy_drug_price_stat.rep_price} 평균, 없으면 {@code null}
 * @param pharmacyCount    가격 정보가 있는 약국 수
 */
public record DrugSummaryResponse(
    Long id,
    String itemSeq,
    String displayName,
    String name,
    String maker,
    String category,
    String form,
    String packageUnit,
    String imageUrl,
    Integer nationalAvgPrice,
    long pharmacyCount
) {
}
