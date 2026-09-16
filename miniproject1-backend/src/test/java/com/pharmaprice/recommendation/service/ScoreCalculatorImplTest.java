package com.pharmaprice.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.common.config.RecommendationProperties.Outlier;
import com.pharmaprice.common.config.RecommendationProperties.Weights;
import com.pharmaprice.recommendation.dto.Badge;
import com.pharmaprice.recommendation.service.ScoreCalculator.Candidate;
import com.pharmaprice.recommendation.service.ScoreCalculator.ScoredCandidate;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code ScoreCalculatorSmokeTest}(T-11 완료 판정용 최소 스모크 테스트)와 별개로,
 * {@code docs/ROADMAP.md} T-12 표의 8개 케이스(#1~#8)를 검증한다.
 *
 * <p>가급적 절대 score 값이 아니라 "어느 pharmacyId가 1위인가"라는 상대 순위로 검증해,
 * 가중치({@code recommendation.weights.*})가 나중에 조정되어도 테스트 의도가 유지되도록
 * 한다. 단 #7(반경 경계 역전)과 #8(뱃지 동시 부여)은 ROADMAP이 구체 조건을 못 박은
 * 케이스라 그 조건을 그대로 고정한다.</p>
 */
class ScoreCalculatorImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);

    private ScoreCalculatorImpl calculatorWith(double priceW, double distanceW, double freshnessW) {
        RecommendationProperties props = new RecommendationProperties(
            new Weights(priceW, distanceW, freshnessW), 30, 90, 180, new Outlier(1.5, 4));
        return new ScoreCalculatorImpl(props);
    }

    /** 기본 가중치(price=0.60, distance=0.25, freshness=0.15) — application.yml 값과 동일. */
    private ScoreCalculatorImpl defaultCalculator() {
        return calculatorWith(0.6, 0.25, 0.15);
    }

    @Test
    void cheaperWinsWhenDistanceAndFreshnessTied() {
        Candidate cheaper = new Candidate(1L, 2000, 500, TODAY, 4);
        Candidate pricier = new Candidate(2L, 3000, 500, TODAY, 4);

        List<ScoredCandidate> result = defaultCalculator().rank(List.of(cheaper, pricier), 2000, TODAY);

        assertThat(result.get(0).candidate().pharmacyId()).isEqualTo(1L);
    }

    @Test
    void closerWinsWhenPriceAndFreshnessTied() {
        Candidate closer = new Candidate(1L, 2500, 200, TODAY, 4);
        Candidate farther = new Candidate(2L, 2500, 1000, TODAY, 4);

        List<ScoredCandidate> result = defaultCalculator().rank(List.of(closer, farther), 2000, TODAY);

        assertThat(result.get(0).candidate().pharmacyId()).isEqualTo(1L);
    }

    @Test
    void fresherWinsWhenPriceAndDistanceTied() {
        Candidate fresher = new Candidate(1L, 2500, 500, TODAY, 4);
        Candidate staler = new Candidate(2L, 2500, 500, TODAY.minusDays(60), 4);

        List<ScoredCandidate> result = defaultCalculator().rank(List.of(fresher, staler), 2000, TODAY);

        assertThat(result.get(0).candidate().pharmacyId()).isEqualTo(1L);
    }

    @Test
    void singleCandidateNeverThrowsAndGetsPerfectPriceScore() {
        Candidate only = new Candidate(1L, 2500, 500, TODAY, 4);
        ScoreCalculatorImpl calculator = defaultCalculator();

        assertThatCode(() -> calculator.rank(List.of(only), 2000, TODAY)).doesNotThrowAnyException();
        assertThat(calculator.rank(List.of(only), 2000, TODAY).get(0).breakdown().priceScore()).isEqualTo(1.0);
    }

    @Test
    void allSamePriceGivesPerfectPriceScoreToEveryone() {
        Candidate a = new Candidate(1L, 2500, 200, TODAY, 4);
        Candidate b = new Candidate(2L, 2500, 900, TODAY.minusDays(10), 2);
        Candidate c = new Candidate(3L, 2500, 1500, TODAY.minusDays(45), 1);

        List<ScoredCandidate> result = defaultCalculator().rank(List.of(a, b, c), 2000, TODAY);

        // 전원 동일가라 P_max == P_min → 0으로 나누기 대신 전원 1.0이어야 한다.
        assertThat(result).allSatisfy(sc -> assertThat(sc.breakdown().priceScore()).isEqualTo(1.0));
    }

    @Test
    void sameInputTwiceProducesIdenticalOrder() {
        Candidate a = new Candidate(1L, 2000, 300, TODAY, 4);
        Candidate b = new Candidate(2L, 2500, 800, TODAY.minusDays(5), 3);
        Candidate c = new Candidate(3L, 3000, 1200, TODAY.minusDays(20), 2);
        List<Candidate> candidates = List.of(a, b, c);
        ScoreCalculatorImpl calculator = defaultCalculator();

        List<Long> firstOrder = calculator.rank(candidates, 2000, TODAY).stream()
            .map(sc -> sc.candidate().pharmacyId()).toList();
        List<Long> secondOrder = calculator.rank(candidates, 2000, TODAY).stream()
            .map(sc -> sc.candidate().pharmacyId()).toList();

        assertThat(secondOrder).containsExactlyElementsOf(firstOrder);
    }

    @Test
    void boundaryDistanceCanFlipCheapestOutOfFirstPlace() {
        // A가 가장 싸지만 반경 경계(distanceScore=0)에 있고, B는 A보다 조금 비싸지만
        // 바로 코앞(distanceScore=0.975)에 있다. C는 A·B보다 훨씬 비싸 가격 정규화
        // 구간을 넓히는 역할만 한다(C가 없으면 2후보 사이의 priceScore 차이가 항상
        // 0↔1 풀스윙이 되어 price 가중치(0.6)가 distance 가중치(0.25)를 절대
        // 못 이기므로 역전이 재현되지 않는다 — 사전에 손계산으로 확인한 필수 장치).
        //
        // P_min=2000, P_max=10000 → priceScore: A=1.0, B=0.9875, C=0.0
        // distanceScore(R=2000): A=0.0, B=0.975, C=0.5
        // score = 0.6*price + 0.25*distance + 0.15*1(전원 오늘 날짜)
        //   A = 0.6*1.0    + 0.25*0.0   + 0.15 = 0.75
        //   B = 0.6*0.9875 + 0.25*0.975 + 0.15 = 0.98625
        //   C = 0.6*0.0    + 0.25*0.5   + 0.15 = 0.275
        // → B가 1위. "가장 싼 A가 1위가 아닌 것"은 버그가 아니라 거리 가중치에 의한
        // 의도된 동작이며, 이 테스트가 그 근거다(ROADMAP T-12 메모).
        Candidate cheapestAtBoundary = new Candidate(1L, 2000, 2000, TODAY, 4);
        Candidate secondCheapestNearby = new Candidate(2L, 2100, 50, TODAY, 4);
        Candidate priceRangeStretcher = new Candidate(3L, 10000, 1000, TODAY, 4);

        List<ScoredCandidate> result = defaultCalculator()
            .rank(List.of(cheapestAtBoundary, secondCheapestNearby, priceRangeStretcher), 2000, TODAY);

        assertThat(result.get(0).candidate().pharmacyId()).isEqualTo(2L);
    }

    @Test
    void lowConfidenceAndStaleBadgesCoexist() {
        Candidate lowConfidenceAndStale = new Candidate(1L, 2500, 500, TODAY.minusDays(40), 1);

        List<ScoredCandidate> result = defaultCalculator().rank(List.of(lowConfidenceAndStale), 2000, TODAY);

        assertThat(result.get(0).badges()).contains(Badge.LOW_CONFIDENCE, Badge.STALE_DATA);
    }
}
