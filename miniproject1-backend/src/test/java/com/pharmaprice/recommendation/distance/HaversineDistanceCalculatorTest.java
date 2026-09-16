package com.pharmaprice.recommendation.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.withinPercentage;

import com.pharmaprice.recommendation.distance.DistanceCalculator.BoundingBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code RecommendationPropertiesTest} 처럼 Spring 컨텍스트·DB 없이 순수 로직만
 * 검증하는 단위 테스트다. {@code AbstractIntegrationTest} 를 상속하지 않으므로
 * 로컬 Postgres 기동 여부와 무관하게 항상 실행된다.
 */
class HaversineDistanceCalculatorTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    void calculatesDistanceBetweenGangnamAndYeoksamWithinOnePercent() {
        double distance = calculator.distanceMeters(37.4979, 127.0276, 37.5006, 127.0366);

        assertThat(distance).isCloseTo(850, withinPercentage(1));
    }

    @Test
    void distanceIsSymmetricAndZeroForSamePoint() {
        double a2b = calculator.distanceMeters(37.4979, 127.0276, 37.5006, 127.0366);
        double b2a = calculator.distanceMeters(37.5006, 127.0366, 37.4979, 127.0276);

        assertThat(a2b).isEqualTo(b2a);
        assertThat(calculator.distanceMeters(37.4979, 127.0276, 37.4979, 127.0276)).isZero();
    }

    @Test
    void boundingBoxFullyContainsTheSearchRadius() {
        double lat = 37.5;
        double lng = 127.0;
        int radiusM = 1000;

        BoundingBox box = calculator.boundingBox(lat, lng, radiusM);

        // 북/남/동/서 경계 좌표는 중심에서 정확히 radiusM 만큼 떨어져 있어야 한다
        // (위도 1도당 거리를 상수로 근사한 것과 구면 Haversine 사이의 오차만 허용).
        assertThat(calculator.distanceMeters(lat, lng, box.maxLat(), lng))
            .isCloseTo(radiusM, withinPercentage(1));
        assertThat(calculator.distanceMeters(lat, lng, box.minLat(), lng))
            .isCloseTo(radiusM, withinPercentage(1));
        assertThat(calculator.distanceMeters(lat, lng, lat, box.maxLng()))
            .isCloseTo(radiusM, withinPercentage(1));
        assertThat(calculator.distanceMeters(lat, lng, lat, box.minLng()))
            .isCloseTo(radiusM, withinPercentage(1));

        // 대각선 모서리는 반경보다 멀다 — 바운딩 박스가 정사각형이라 모서리에
        // 반경 밖 지점이 섞이므로, 이 컴포넌트를 쓰는 쪽이 반드시 distanceMeters로
        // 2차 필터링해야 하는 이유를 보여준다.
        assertThat(calculator.distanceMeters(lat, lng, box.maxLat(), box.maxLng()))
            .isGreaterThan(radiusM);
    }

    @Test
    void longitudeDeltaGrowsWithLatitudeDueToCosCorrection() {
        int radiusM = 1000;

        BoundingBox lowLatBox = calculator.boundingBox(33, 127.0, radiusM);
        BoundingBox highLatBox = calculator.boundingBox(38, 127.0, radiusM);

        double lowLatLngDelta = lowLatBox.maxLng() - 127.0;
        double highLatLngDelta = highLatBox.maxLng() - 127.0;

        // cos(lat) 보정이 없다면 두 값이 같아진다 — 위도가 높을수록(경도 1도의
        // 실제 거리가 짧아지므로) lngDelta가 더 커져야 한다.
        assertThat(highLatLngDelta).isGreaterThan(lowLatLngDelta);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 1000, 2000, 5000})
    void allowsOnlyThePredefinedRadiusValues(int radiusM) {
        assertThat(calculator.boundingBox(37.5, 127.0, radiusM)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -100, 1500, 10_000})
    void rejectsRadiusValuesOutsideThePredefinedSet(int radiusM) {
        assertThatThrownBy(() -> calculator.boundingBox(37.5, 127.0, radiusM))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateCoordinateAcceptsCoordinatesInsideKorea() {
        // 서울시청
        assertThatCode(() -> DistanceCalculator.validateCoordinate(37.5665, 126.9780))
            .doesNotThrowAnyException();
    }

    @Test
    void validateCoordinateRejectsCoordinatesOutsideKorea() {
        assertThatThrownBy(() -> DistanceCalculator.validateCoordinate(10, 200))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
