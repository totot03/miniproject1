package com.pharmaprice.pharmacy.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code docs/API.md} §4 {@code GET /api/v1/pharmacies/{pharmacyId}/drugs/{drugId}/history}
 * 응답 필드. {@code points} 는 {@code purchasedAt} 오름차순.
 */
public record PriceHistoryResponse(
    Long pharmacyId,
    Long drugId,
    List<PricePoint> points
) {

    /**
     * @param flagged 이상치 자동 탐지로 통계에서 제외된 제보인지. {@code true}여도 목록에서
     *                빼지 않는다 — 프론트가 회색 점선으로 표시해 "이 값은 대표가격 계산에서
     *                빠졌다"를 보여주는 유일한 지점이다.
     */
    public record PricePoint(LocalDate purchasedAt, int price, boolean flagged) {
    }
}
