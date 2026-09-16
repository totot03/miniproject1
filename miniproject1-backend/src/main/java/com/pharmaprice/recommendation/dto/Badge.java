package com.pharmaprice.recommendation.dto;

/**
 * 검색 결과 카드에 붙는 추천 배지. docs/API.md §5 응답의 {@code badges} 필드에 대응한다.
 *
 * <p>한 candidate가 여러 배지를 동시에 가질 수 있다
 * (예: 제보 1건 + 30일 초과 경과 → LOW_CONFIDENCE, STALE_DATA 둘 다).</p>
 */
public enum Badge {

    /** 정렬(score DESC → repPrice ASC → distanceM ASC → pharmacyId ASC) 후 1위 */
    LOWEST_PRICE,

    /** 유효 제보가 1건뿐이라 대표가격의 신뢰도가 낮음 */
    LOW_CONFIDENCE,

    /** 마지막 제보로부터 30일 초과 경과 */
    STALE_DATA,

    /**
     * 후보군 내 최단거리. 동률이면(같은 distanceM) 그 값을 가진 모든 candidate에 부여한다 —
     * 명세에 동률 처리가 명시돼 있지 않아 채택한 기본값이며, T-12에서 재검토될 수 있다.
     */
    NEAREST
}
