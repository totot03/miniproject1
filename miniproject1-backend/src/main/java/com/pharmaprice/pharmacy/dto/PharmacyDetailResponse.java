package com.pharmaprice.pharmacy.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * {@code docs/API.md} §4 {@code GET /api/v1/pharmacies/{pharmacyId}} 상세 응답 필드.
 *
 * @param businessHours {"mon":["09:00","19:00"], ..., "holiday":null} 형태. {@code Pharmacy}
 *                       엔티티의 jsonb 매핑을 그대로 재사용한다(별도 변환 없음).
 * @param distanceM     lat/lng를 넘겼을 때만 채워지고, 아니면 {@code null}
 */
public record PharmacyDetailResponse(
    Long id,
    String name,
    String addressRoad,
    String addressJibun,
    double lat,
    double lng,
    String phone,
    Map<String, List<String>> businessHours,
    Long distanceM,
    RegionInfo region,
    List<DrugPriceItem> drugPrices
) {

    /**
     * @param nationalAvgPrice   전국 {@code pharmacy_drug_price_stat.rep_price} 평균, 없으면 {@code null}
     * @param diffFromNationalAvg {@code repPrice - nationalAvgPrice}. nationalAvgPrice가 없으면 {@code null}
     */
    public record DrugPriceItem(
        Long drugId,
        String displayName,
        String packageUnit,
        int repPrice,
        int minPrice,
        int maxPrice,
        int avgPrice,
        int reportCount,
        LocalDate lastReportedAt,
        Integer nationalAvgPrice,
        Integer diffFromNationalAvg
    ) {
    }
}
