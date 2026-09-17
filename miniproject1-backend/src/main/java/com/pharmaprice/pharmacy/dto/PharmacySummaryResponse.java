package com.pharmaprice.pharmacy.dto;

/**
 * {@code docs/API.md} §4 {@code GET /api/v1/pharmacies} 목록 응답 필드.
 *
 * @param distanceM lat/lng를 넘겼을 때만 채워지고, 아니면 {@code null}
 * @param region    지역 정보가 없는 약국이면 {@code null}
 */
public record PharmacySummaryResponse(
    Long id,
    String name,
    String addressRoad,
    double lat,
    double lng,
    String phone,
    Long distanceM,
    RegionInfo region
) {
}
