package com.pharmaprice.recommendation.service;

import com.pharmaprice.recommendation.dto.SearchResponse;

public interface SearchService {

    /**
     * 위치(GPS 또는 지역 폴백) + 약품 → 최저가 추천 결과. {@code docs/API.md} §5.
     *
     * @param lat        사용자 위도. {@code lng}와 함께 오면 GPS 우선(둘 중 하나가 없으면 무시)
     * @param lng        사용자 경도
     * @param regionCode {@code lat}/{@code lng}가 없을 때의 폴백 좌표 출처
     * @param radius     미터. 허용값은 {@link com.pharmaprice.recommendation.distance.DistanceCalculator} 구현체가 정의
     * @param sort       {@code "SCORE"}(기본) \| {@code "PRICE"} \| {@code "DISTANCE"}. 알 수 없는 값은 SCORE로 취급
     * @param limit      최대 반환 건수 (1~50으로 clamp)
     * @throws com.pharmaprice.recommendation.exception.DrugNotFoundException 약품이 없거나 전문의약품이면 404
     * @throws com.pharmaprice.common.exception.InvalidRequestException 위치 파라미터가 전부 없으면 400
     * @throws com.pharmaprice.recommendation.exception.InvalidRadiusException 허용 안 된 radius면 400
     * @throws com.pharmaprice.recommendation.exception.InvalidCoordinateException 좌표가 대한민국 범위 밖이면 400
     */
    SearchResponse search(Long drugId, Double lat, Double lng, String regionCode, int radius, String sort, int limit);
}
