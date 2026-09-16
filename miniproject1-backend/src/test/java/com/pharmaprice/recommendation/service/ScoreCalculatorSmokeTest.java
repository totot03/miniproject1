package com.pharmaprice.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.common.config.RecommendationProperties.Outlier;
import com.pharmaprice.common.config.RecommendationProperties.Weights;
import com.pharmaprice.recommendation.service.ScoreCalculator.Candidate;
import com.pharmaprice.recommendation.service.ScoreCalculator.ScoredCandidate;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code HaversineDistanceCalculatorTest} 처럼 Spring 컨텍스트·DB 없이 순수 로직만
 * 검증하는 단위 테스트다.
 *
 * <p>docs/ROADMAP.md T-11 완료 판정 4개 항목만 확인하는 최소 스모크 테스트다.
 * 뱃지 동시 부여, 경계값, 결정성 등 8종 전체 케이스는 T-12
 * {@code ScoreCalculatorImplTest} 에서 다룬다 — 여기서는 중복하지 않는다.</p>
 */
class ScoreCalculatorSmokeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);

    private ScoreCalculatorImpl calculatorWith(double priceW, double distanceW, double freshnessW) {
        RecommendationProperties props = new RecommendationProperties(
            new Weights(priceW, distanceW, freshnessW), 30, 90, 180, new Outlier(1.5, 4));
        return new ScoreCalculatorImpl(props);
    }

    @Test
    void singleCandidateAlwaysGetsPerfectPriceScore() {
        ScoreCalculatorImpl calculator = calculatorWith(0.6, 0.25, 0.15);
        Candidate only = new Candidate(1L, 3000, 500, TODAY, 3);

        List<ScoredCandidate> result = calculator.rank(List.of(only), 2000, TODAY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).breakdown().priceScore()).isEqualTo(1.0);
    }

    @Test
    void scoreBreakdownIsAlwaysPopulated() {
        ScoreCalculatorImpl calculator = calculatorWith(0.6, 0.25, 0.15);
        Candidate a = new Candidate(1L, 2600, 340, TODAY, 4);
        Candidate b = new Candidate(2L, 2900, 180, TODAY.minusDays(87), 1);

        List<ScoredCandidate> result = calculator.rank(List.of(a, b), 2000, TODAY);

        assertThat(result).allSatisfy(sc -> {
            assertThat(sc.breakdown()).isNotNull();
            assertThat(sc.breakdown().weights()).isNotNull();
        });
    }

    @Test
    void changingWeightsChangesRanking() {
        Candidate cheaperButFar = new Candidate(1L, 2000, 1900, TODAY, 4);
        Candidate pricierButClose = new Candidate(2L, 3900, 100, TODAY, 4);

        long topWithPriceHeavyWeights = calculatorWith(0.9, 0.05, 0.05)
            .rank(List.of(cheaperButFar, pricierButClose), 2000, TODAY)
            .get(0).candidate().pharmacyId();
        long topWithDistanceHeavyWeights = calculatorWith(0.05, 0.9, 0.05)
            .rank(List.of(cheaperButFar, pricierButClose), 2000, TODAY)
            .get(0).candidate().pharmacyId();

        assertThat(topWithPriceHeavyWeights).isEqualTo(1L);
        assertThat(topWithDistanceHeavyWeights).isEqualTo(2L);
    }

    @Test
    void formulaMatchesApiAndPrdSpecForTwoCandidates() {
        // P_min=2600, P_max=2900
        Candidate a = new Candidate(1L, 2600, 340, TODAY, 4);
        Candidate b = new Candidate(2L, 2900, 180, TODAY, 4);

        List<ScoredCandidate> result = calculatorWith(0.6, 0.25, 0.15).rank(List.of(a, b), 2000, TODAY);

        ScoredCandidate scoredA = result.stream()
            .filter(sc -> sc.candidate().pharmacyId() == 1L)
            .findFirst().orElseThrow();

        // priceScore=(2900-2600)/(2900-2600)=1.0, distanceScore=1-340/2000=0.83, freshnessScore=0.5^0=1.0
        // score = 0.6*1.0 + 0.25*0.83 + 0.15*1.0 = 0.9575
        assertThat(scoredA.breakdown().priceScore()).isEqualTo(1.0);
        assertThat(scoredA.breakdown().distanceScore()).isEqualTo(0.83);
        assertThat(scoredA.score()).isEqualTo(0.9575);
    }
}
