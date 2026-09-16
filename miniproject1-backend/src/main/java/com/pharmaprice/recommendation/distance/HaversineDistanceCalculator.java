package com.pharmaprice.recommendation.distance;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * PostGIS 없이 순수 Java로 계산하는 {@link DistanceCalculator} 구현체.
 *
 * <p>{@link #boundingBox}로 SQL 단에서 인덱스({@code idx_pharmacy_lat_lng})를 태워
 * 후보를 정사각형 범위로 좁히고, {@link #distanceMeters}(Haversine 공식)로 정확한
 * 거리를 구해 2차 필터링한다({@code docs/DATABASE.md} §4). 두 메서드는 항상 짝으로
 * 쓰여야 한다 — 바운딩 박스만으로는 모서리에 반경 밖 지점이 섞인다.
 */
@Component
public class HaversineDistanceCalculator implements DistanceCalculator {

    /** 지구 평균 반지름(미터). */
    private static final double EARTH_RADIUS_M = 6_371_000;

    /** 위도 1도당 실제 거리(미터). 자오선은 거의 원이라 위도·지구 어디서나 거의 일정하다. */
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    /** 검색 API가 허용하는 반경(미터) 값. 그 외는 {@code 400 INVALID_RADIUS}(T-35)로 변환된다. */
    private static final Set<Integer> ALLOWED_RADII_M = Set.of(500, 1000, 2000, 5000);

    @Override
    public BoundingBox boundingBox(double lat, double lng, int radiusM) {
        if (!ALLOWED_RADII_M.contains(radiusM)) {
            throw new IllegalArgumentException(
                "허용되지 않은 반경입니다: " + radiusM + " (허용값: " + ALLOWED_RADII_M + ")");
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
