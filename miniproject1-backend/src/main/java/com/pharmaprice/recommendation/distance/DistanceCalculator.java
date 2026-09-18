package com.pharmaprice.recommendation.distance;

import com.pharmaprice.recommendation.exception.InvalidCoordinateException;

/**
 * 두 좌표 사이의 거리와, 반경 검색을 위한 바운딩 박스를 계산한다.
 *
 * <p>지금은 PostGIS 없이 {@link HaversineDistanceCalculator}(바운딩 박스 선필터 +
 * Haversine 정확 거리)로 구현하지만, 이후 PostGIS {@code ST_DWithin} 구현체로
 * 교체할 수 있도록 인터페이스 뒤에 둔다. 소비자(T-10 이후)는 구체 구현이 아니라
 * 이 인터페이스에만 의존해야 한다.
 */
public interface DistanceCalculator {

    /**
     * 중심 좌표에서 반경 {@code radiusM} 이내를 완전히 포함하는 정사각형 범위를 구한다.
     *
     * <p>정사각형이므로 네 모서리에는 반경 밖 지점이 섞인다. SQL 등에서 이 범위로
     * 인덱스({@code idx_pharmacy_lat_lng})를 태워 후보를 좁힌 뒤, 반드시
     * {@link #distanceMeters}로 반경 안쪽인지 2차 필터링해야 한다.
     *
     * @param radiusM 허용값은 구현체가 정의한다(예: {@link HaversineDistanceCalculator}는 500/1000/2000/5000)
     */
    BoundingBox boundingBox(double lat, double lng, int radiusM);

    /** 두 좌표 사이의 실제 거리를 미터 단위로 계산한다. */
    double distanceMeters(double lat1, double lng1, double lat2, double lng2);

    /** 위도·경도 최소/최대로 이루어진 사각 범위. */
    record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {}

    /**
     * 좌표가 대한민국 범위(위도 33~39, 경도 124~132) 안인지 검증한다.
     *
     * <p>{@code pharmacy} 테이블에 저장된 좌표(DB {@code CHECK} 제약으로 이미 전 지구
     * 범위까지 방어됨)를 매 계산마다 재검증하는 용도가 아니라, 검색 API가 받는
     * 사용자 입력 좌표(쿼리 파라미터)를 {@code 400 INVALID_COORDINATE} 로 변환하기
     * 전에 호출하는 유틸이다.
     *
     * @throws InvalidCoordinateException 범위를 벗어난 경우
     */
    static void validateCoordinate(double lat, double lng) {
        if (lat < 33 || lat > 39 || lng < 124 || lng > 132) {
            throw new InvalidCoordinateException(
                "좌표가 대한민국 범위를 벗어났습니다: lat=%s, lng=%s".formatted(lat, lng));
        }
    }
}
