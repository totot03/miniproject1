package com.pharmaprice.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.common.config.RecommendationProperties.Outlier;
import com.pharmaprice.common.config.RecommendationProperties.Weights;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.recommendation.distance.DistanceCalculator;
import com.pharmaprice.recommendation.distance.HaversineDistanceCalculator;
import com.pharmaprice.recommendation.dto.DataSource;
import com.pharmaprice.recommendation.dto.SearchResponse;
import com.pharmaprice.recommendation.repository.SearchQueryRepository;
import com.pharmaprice.recommendation.repository.SearchQueryRepository.CandidateRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code docs/ROADMAP.md} T-15 서비스 로직 검증. {@code DrugRepository}/
 * {@code RegionRepository}/{@code SearchQueryRepository}는 Mockito 목이지만,
 * {@link DistanceCalculator}({@link HaversineDistanceCalculator})와
 * {@link ScoreCalculator}({@link ScoreCalculatorImpl})는 **실제 구현체**를
 * 그대로 사용한다 — T-09/T-11이 DB 없이도 테스트 가능하도록 설계해둔 순수
 * 로직을 그대로 태워, 바운딩 박스/거리/Score 계산이 실제로 맞물려 동작하는지
 * 확인한다({@code ScoreCalculatorImplTest.java:29-38}과 같은 조합 패턴).
 */
class SearchServiceImplTest {

    private final DrugRepository drugRepository = mock(DrugRepository.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final SearchQueryRepository searchQueryRepository = mock(SearchQueryRepository.class);
    private final DistanceCalculator distanceCalculator = new HaversineDistanceCalculator();
    private final RecommendationProperties properties = new RecommendationProperties(
        new Weights(0.6, 0.25, 0.15), 30, 90, 180, new Outlier(1.5, 4));
    private final ScoreCalculator scoreCalculator = new ScoreCalculatorImpl(properties);

    private SearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SearchServiceImpl(drugRepository, regionRepository, searchQueryRepository,
            distanceCalculator, scoreCalculator, properties);
        // 바운딩 박스 값 자체는 이 클래스의 관심사가 아니다(HaversineDistanceCalculator가
        // 이미 검증됨, T-09) — 어떤 박스가 오든 미리 준비한 후보를 그대로 돌려준다.
        given(searchQueryRepository.findCandidatesInBoundingBox(
            anyLong(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .willReturn(List.of());
        given(searchQueryRepository.findDistinctSources(anyLong(), anyList(), anyInt()))
            .willReturn(List.of("SEED"));
    }

    private Drug otcDrug(long id, String displayName) {
        return Drug.builder().id(id).name(displayName).displayName(displayName)
            .category("해열진통").packageUnit("8정").otcFlag(true).build();
    }

    private CandidateRow row(long pharmacyId, double lat, double lng, int repPrice, int reportCount, LocalDate lastReportedAt) {
        CandidateRow row = mock(CandidateRow.class);
        given(row.getPharmacyId()).willReturn(pharmacyId);
        given(row.getName()).willReturn("약국" + pharmacyId);
        given(row.getAddressRoad()).willReturn("주소" + pharmacyId);
        given(row.getLat()).willReturn(lat);
        given(row.getLng()).willReturn(lng);
        given(row.getPhone()).willReturn("02-000-0000");
        given(row.getRepPrice()).willReturn(repPrice);
        given(row.getMinPrice()).willReturn(repPrice);
        given(row.getAvgPrice()).willReturn(repPrice);
        given(row.getReportCount()).willReturn(reportCount);
        given(row.getLastReportedAt()).willReturn(lastReportedAt);
        return row;
    }

    @Test
    void searchByRegionCodeUsesRegionCenterAsLocation() {
        given(drugRepository.findById(1L)).willReturn(Optional.of(otcDrug(1L, "지역폴백약품")));
        Region region = Region.builder().code("11680").sido("서울특별시").sigungu("강남구")
            .centerLat(37.50).centerLng(127.00).build();
        given(regionRepository.findById("11680")).willReturn(Optional.of(region));
        CandidateRow candidate = row(101L, 37.5001, 127.00, 3000, 3, LocalDate.now());
        given(searchQueryRepository.findCandidatesInBoundingBox(eq(1L), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .willReturn(List.of(candidate));

        SearchResponse response = service.search(1L, null, null, "11680", 2000, "SCORE", 20);

        assertThat(response.query().locationSource()).isEqualTo("REGION");
        assertThat(response.query().lat()).isEqualTo(37.50);
        assertThat(response.results()).hasSize(1);
    }

    @Test
    void searchWithoutLatLngOrRegionCodeThrows400() {
        given(drugRepository.findById(1L)).willReturn(Optional.of(otcDrug(1L, "약품")));

        assertThatThrownBy(() -> service.search(1L, null, null, null, 2000, "SCORE", 20))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void searchWithDisallowedRadiusThrows() {
        // boundingBox()는 더 이상 500/1000/2000/5000 제한을 두지 않는다(T-19에서
        // /pharmacies가 임의 반경을 써야 해 이 검증을 여기로 옮겼다) — 이 서비스가
        // 직접 검증하는지 확인한다.
        given(drugRepository.findById(1L)).willReturn(Optional.of(otcDrug(1L, "약품")));

        assertThatThrownBy(() -> service.search(1L, 37.5, 127.0, null, 1500, "SCORE", 20))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(1L, 37.5, 127.0, null, 10_000, "SCORE", 20))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchWithMissingDrugThrows404() {
        given(drugRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.search(999L, 37.5, 127.0, null, 2000, "SCORE", 20))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void searchWithPrescriptionOnlyDrugThrows404() {
        Drug prescriptionOnly = Drug.builder().id(2L).name("전문약").displayName("전문약")
            .category("기타").packageUnit("1개").otcFlag(false).build();
        given(drugRepository.findById(2L)).willReturn(Optional.of(prescriptionOnly));

        assertThatThrownBy(() -> service.search(2L, 37.5, 127.0, null, 2000, "SCORE", 20))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void sortByPriceReordersResultsButKeepsScoreAndRecommendedFromScoreRanking() {
        given(drugRepository.findById(1L)).willReturn(Optional.of(otcDrug(1L, "정렬테스트약품")));
        LocalDate today = LocalDate.now();
        // X: 최저가(2000)지만 반경 끝(1999m) — SCORE에서는 거리 감점이 커서 밀린다.
        // Y: 중간가(2100)에 매우 가까움(1m) — 이 가중치(price .6/distance .25)에서 SCORE 1위가 된다.
        // Z: 최고가(2500)로 가장 가깝지만(5m) 가격 감점이 커서 SCORE 최하위.
        CandidateRow rowX = row(201L, 37.517957, 127.00, 2000, 5, today);
        CandidateRow rowY = row(202L, 37.50000898, 127.00, 2100, 5, today);
        CandidateRow rowZ = row(203L, 37.5000449, 127.00, 2500, 5, today);
        List<CandidateRow> rows = List.of(rowX, rowY, rowZ);
        given(searchQueryRepository.findCandidatesInBoundingBox(eq(1L), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .willReturn(rows);

        SearchResponse scoreResponse = service.search(1L, 37.50, 127.00, null, 2000, "SCORE", 20);
        SearchResponse priceResponse = service.search(1L, 37.50, 127.00, null, 2000, "PRICE", 20);

        // PRICE 정렬 결과는 repPrice 오름차순이어야 한다.
        assertThat(priceResponse.results()).extracting(r -> r.price().repPrice())
            .containsExactly(2000, 2100, 2500);

        // sort가 실제로 1위를 바꾼다는 증거 — SCORE 1위(Y)와 PRICE 1위(X)가 다르다.
        long scoreTopId = scoreResponse.results().get(0).pharmacy().id();
        long priceTopId = priceResponse.results().get(0).pharmacy().id();
        assertThat(scoreTopId).isEqualTo(202L);
        assertThat(priceTopId).isEqualTo(201L);
        assertThat(priceTopId).isNotEqualTo(scoreTopId);

        // SCORE 1위였던 약국은 PRICE 응답 안에서도 recommended=true, score 값 그대로 유지된다.
        var scoreTopInPriceResponse = priceResponse.results().stream()
            .filter(r -> r.pharmacy().id() == scoreTopId).findFirst().orElseThrow();
        assertThat(scoreTopInPriceResponse.recommended()).isTrue();
        assertThat(scoreTopInPriceResponse.score()).isEqualTo(scoreResponse.results().get(0).score());
        assertThat(scoreResponse.results().get(0).recommended()).isTrue();
    }

    @Test
    void emptyResultsProducesSuggestionWithNextRadiusEstimatedCount() {
        given(drugRepository.findById(1L)).willReturn(Optional.of(otcDrug(1L, "빈결과약품")));
        // 사용자 위치에서 약 3.3km 거리 — 반경 2000엔 걸리지 않고, 확대 반경 5000엔 걸린다.
        CandidateRow farRow = row(301L, 37.53, 127.00, 3000, 3, LocalDate.now());
        given(searchQueryRepository.findCandidatesInBoundingBox(eq(1L), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .willReturn(List.of(farRow));

        SearchResponse response = service.search(1L, 37.50, 127.00, null, 2000, "SCORE", 20);

        assertThat(response.results()).isEmpty();
        assertThat(response.summary().resultCount()).isZero();
        assertThat(response.summary().candidateAvgPrice()).isNull();
        assertThat(response.suggestion()).isNotNull();
        assertThat(response.suggestion().recommendedRadius()).isEqualTo(5000);
        assertThat(response.suggestion().estimatedCount()).isEqualTo(1);
    }

    @Test
    void emptyResultsAtMaxRadiusHasNoSuggestion() {
        given(drugRepository.findById(1L)).willReturn(Optional.of(otcDrug(1L, "빈결과약품")));
        given(searchQueryRepository.findCandidatesInBoundingBox(eq(1L), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .willReturn(List.of());

        SearchResponse response = service.search(1L, 37.50, 127.00, null, 5000, "SCORE", 20);

        assertThat(response.results()).isEmpty();
        assertThat(response.suggestion()).isNull();
    }

    @Test
    void summaryReflectsAllCandidatesBeforeLimitIsApplied() {
        given(drugRepository.findById(1L)).willReturn(Optional.of(otcDrug(1L, "요약테스트약품")));
        LocalDate today = LocalDate.now();
        List<CandidateRow> rows = List.of(
            row(401L, 37.5001, 127.00, 1000, 5, today),
            row(402L, 37.5002, 127.00, 2000, 5, today),
            row(403L, 37.5003, 127.00, 3000, 5, today),
            row(404L, 37.5004, 127.00, 4000, 5, today),
            row(405L, 37.5005, 127.00, 5000, 5, today)
        );
        given(searchQueryRepository.findCandidatesInBoundingBox(eq(1L), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .willReturn(rows);

        SearchResponse response = service.search(1L, 37.50, 127.00, null, 2000, "SCORE", 2);

        assertThat(response.summary().resultCount()).isEqualTo(2);
        assertThat(response.summary().candidateAvgPrice()).isEqualTo(3000);
        assertThat(response.summary().candidateMinPrice()).isEqualTo(1000);
        assertThat(response.summary().candidateMaxPrice()).isEqualTo(5000);
        assertThat(response.summary().maxSaving()).isEqualTo(4000);
        assertThat(response.results()).hasSize(2);
    }

    @Test
    void dataSourceIsMixedWhenSeedAndUserReportsBothExist() {
        given(drugRepository.findById(1L)).willReturn(Optional.of(otcDrug(1L, "데이터소스테스트")));
        CandidateRow candidate = row(501L, 37.5001, 127.00, 3000, 3, LocalDate.now());
        given(searchQueryRepository.findCandidatesInBoundingBox(eq(1L), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .willReturn(List.of(candidate));
        given(searchQueryRepository.findDistinctSources(eq(1L), anyList(), anyInt()))
            .willReturn(List.of("SEED", "FORM"));

        SearchResponse response = service.search(1L, 37.50, 127.00, null, 2000, "SCORE", 20);

        assertThat(response.dataSource()).isEqualTo(DataSource.MIXED);
    }
}
