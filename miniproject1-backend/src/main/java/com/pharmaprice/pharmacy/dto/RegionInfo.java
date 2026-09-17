package com.pharmaprice.pharmacy.dto;

/**
 * {@code docs/API.md} §4/§5 응답이 공유하는 소형 지역 정보. {@code RegionGroupResponse.SigunguItem}은
 * 지역 목록 화면 전용(centerLat/centerLng/pharmacyCount 포함)이라 필드가 달라 재사용하지 않는다.
 */
public record RegionInfo(
    String code,
    String sido,
    String sigungu
) {
}
