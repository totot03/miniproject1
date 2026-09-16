package com.pharmaprice.pharmacy.dto;

import java.util.List;

/**
 * {@code docs/API.md} §7 {@code GET /api/v1/regions} 응답 필드. 필드명·구조는
 * API.md 예시 JSON과 정확히 일치시킨다(camelCase) — 시도별로 그룹핑된 배열이며
 * 페이지네이션 래퍼({@code PageResponse})는 쓰지 않는다(전체 ~250건).
 *
 * @param sido     시도명 (예: {@code "서울특별시"})
 * @param sigungus 해당 시도에 속한 시군구 목록
 */
public record RegionGroupResponse(
    String sido,
    List<SigunguItem> sigungus
) {

    /**
     * @param code         행정표준코드
     * @param sigungu      시군구명
     * @param centerLat    구역 중심 위도
     * @param centerLng    구역 중심 경도
     * @param pharmacyCount 활성 약국 수 — 0이면 프론트에서 비활성 처리 대상
     */
    public record SigunguItem(
        String code,
        String sigungu,
        double centerLat,
        double centerLng,
        long pharmacyCount
    ) {
    }
}
