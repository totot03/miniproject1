package com.pharmaprice.recommendation.distance;

import org.springframework.stereotype.Component;

/**
 * PostGIS 없이 순수 Java로 계산하는 {@link DistanceCalculator} 구현체.
 *
 * <p>{@link #boundingBox}로 SQL 단에서 인덱스({@code idx_pharmacy_lat_lng})를 태워
 * 후보를 정사각형 범위로 좁히고, {@link #distanceMeters}(Haversine 공식)로 정확한
 * 거리를 구해 2차 필터링한다({@code docs/DATABASE.md} §4). 두 메서드는 항상 짝으로
 * 쓰여야 한다 — 바운딩 박스만으로는 모서리에 반경 밖 지점이 섞인다.
 *
 * <p><b>허용 반경값은 여기서 강제하지 않는다.</b> 이 컴포넌트는 여러 API(T-15
 * {@code /search}, T-19 {@code /pharmacies})가 공유하는 순수 기하 유틸이고, API마다
 * 허용하는 반경 규칙이 다르다({@code /search}는 500/1000/2000/5000 고정값,
 * {@code /pharmacies}는 최대 10000의 임의값 — {@code docs/API.md} §4·§5). 특정 API의
 * 반경 enum 검증은 그 API의 서비스 계층(예: {@code SearchServiceImpl})이 맡는다.</p>
 */
@Component
public class HaversineDistanceCalculator implements DistanceCalculator {

    /** 지구 평균 반지름(미터). */
    private static final double EARTH_RADIUS_M = 6_371_000;

    /** 위도 1도당 실제 거리(미터). 자오선은 거의 원이라 위도·지구 어디서나 거의 일정하다. */
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    @Override
    public BoundingBox boundingBox(double lat, double lng, int radiusM) {
        if (radiusM <= 0) {
            throw new IllegalArgumentException("반경은 0보다 커야 합니다: " + radiusM);
        }
        double latDelta = radiusM / METERS_PER_DEGREE_LAT;
        // 경도 1도의 실제 거리는 위도가 높아질수록 cos(위도)배로 줄어든다(위선의 반지름이
        // 좁아지기 때문). 이 보정을 빼먹으면 한국 위도(37도 부근)에서 동서 방향으로
        // 약 25% 넓은 바운딩 박스가 만들어진다.
        double lngDelta = radiusM / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat)));
        return new BoundingBox(lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta);
    }

    @Override
    public double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLng / 2), 2);
        return EARTH_RADIUS_M * 2 * Math.asin(Math.sqrt(a));
    }
}
