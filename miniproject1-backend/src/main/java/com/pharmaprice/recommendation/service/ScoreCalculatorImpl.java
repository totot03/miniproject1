package com.pharmaprice.recommendation.service;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.common.config.RecommendationProperties.Weights;
import com.pharmaprice.recommendation.dto.Badge;
import com.pharmaprice.recommendation.dto.ScoreBreakdown;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * docs/API.md §5 Score 계산 명세의 순수 Java 구현체.
 *
 * <p>SQL이 아니라 이 클래스가 점수와 정렬을 전담한다(ROADMAP T-11 설계 원칙) —
 * 후보가 수백 건 수준에서는 성능 차이가 없고, DB 없는 단위 테스트(T-12)가
 * 가능해야 하기 때문이다.</p>
 */
@Service
@RequiredArgsConstructor
public class ScoreCalculatorImpl implements ScoreCalculator {

    /** docs/API.md §5 "모든 score는 소수점 4자리로 반올림한다". */
    private static final int SCORE_SCALE = 4;

    /** score DESC → repPrice ASC → distanceM ASC → pharmacyId ASC. 마지막 tiebreak가 결정성을 보장한다. */
    private static final Comparator<ScoredCandidate> RANKING_ORDER =
        Comparator.comparingDouble(ScoredCandidate::score).reversed()
            .thenComparingInt((ScoredCandidate sc) -> sc.candidate().repPrice())
            .thenComparingDouble(sc -> sc.candidate().distanceM())
            .thenComparingLong(sc -> sc.candidate().pharmacyId());

    private final RecommendationProperties properties;

    @Override
    public List<ScoredCandidate> rank(List<Candidate> candidates, int radiusM, LocalDate today) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        int pMin = candidates.stream().mapToInt(Candidate::repPrice).min().orElseThrow();
        int pMax = candidates.stream().mapToInt(Candidate::repPrice).max().orElseThrow();
        double minDistance = candidates.stream().mapToDouble(Candidate::distanceM).min().orElseThrow();
        Weights weights = properties.weights();

        // 1단계: candidate별 score/breakdown과, LOWEST_PRICE를 제외한 나머지 뱃지를 계산한다.
        // LOWEST_PRICE는 "정렬 후 1위"로 정의되므로 정렬 전에는 판정할 수 없다.
        List<ScoredCandidate> sorted = candidates.stream()
            .map(c -> toScoredCandidate(c, pMin, pMax, radiusM, today, weights, minDistance))
            .sorted(RANKING_ORDER)
            .toList();

        // 2단계: 정렬이 끝난 뒤 인덱스 0에만 LOWEST_PRICE를 얹어 최종 리스트를 조립한다.
        List<ScoredCandidate> result = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            ScoredCandidate sc = sorted.get(i);
            if (i == 0) {
                List<Badge> badgesWithLowestPrice = new ArrayList<>(sc.badges().size() + 1);
                badgesWithLowestPrice.add(Badge.LOWEST_PRICE);
                badgesWithLowestPrice.addAll(sc.badges());
                result.add(new ScoredCandidate(sc.candidate(), sc.score(), sc.breakdown(), badgesWithLowestPrice));
            } else {
                result.add(sc);
            }
        }
        return result;
    }

    private ScoredCandidate toScoredCandidate(Candidate c, int pMin, int pMax, int radiusM, LocalDate today,
                                               Weights weights, double minDistance) {
        double priceScore = priceScore(c, pMin, pMax);
        double distanceScore = distanceScore(c, radiusM);
        double freshnessScore = freshnessScore(c, today);

        // 원시(미반올림) 서브스코어로 최종 score를 계산한 뒤, 응답/breakdown에
        // 채울 때만 반올림한다 — 중간 계산에서 먼저 반올림하면 오차가 누적된다.
        double rawScore = weights.price() * priceScore
            + weights.distance() * distanceScore
            + weights.freshness() * freshnessScore;

        ScoreBreakdown breakdown = new ScoreBreakdown(
            round(priceScore), round(distanceScore), round(freshnessScore),
            new ScoreBreakdown.Weights(weights.price(), weights.distance(), weights.freshness()));

        return new ScoredCandidate(c, round(rawScore), breakdown, badgesFor(c, today, minDistance));
    }

    /** P_max == P_min 이면(후보 1개이거나 전원 동일가) 0으로 나누기 대신 1.0을 준다. */
    private double priceScore(Candidate c, int pMin, int pMax) {
        if (pMax == pMin) {
            return 1.0;
        }
        return (double) (pMax - c.repPrice()) / (pMax - pMin);
    }

    private double distanceScore(Candidate c, int radiusM) {
        double raw = 1 - (c.distanceM() / radiusM);
        return Math.max(0.0, Math.min(1.0, raw));
    }

    private double freshnessScore(Candidate c, LocalDate today) {
        double halfLifeDays = properties.freshnessHalfLifeDays(); // 하드코딩 금지 — yml 주입값 사용
        return Math.pow(0.5, ageDays(c, today) / halfLifeDays);
    }

    private long ageDays(Candidate c, LocalDate today) {
        return ChronoUnit.DAYS.between(c.lastReportedAt(), today);
    }

    private List<Badge> badgesFor(Candidate c, LocalDate today, double minDistance) {
        List<Badge> badges = new ArrayList<>();
        if (c.reportCount() == 1) {
            badges.add(Badge.LOW_CONFIDENCE);
        }
        if (ageDays(c, today) > 30) {
            badges.add(Badge.STALE_DATA);
        }
        if (c.distanceM() == minDistance) {
            badges.add(Badge.NEAREST);
        }
        return badges;
    }

    /**
     * BigDecimal 기반 반올림. {@code Math.round(v * 10000) / 10000.0} 은 이진 부동소수점
     * 곱셈·나눗셈 과정에서 오차가 낄 수 있어, 대신 {@code Double.toString} 경로를 타는
     * {@link BigDecimal#valueOf} 로 변환한 뒤 {@link RoundingMode#HALF_UP} 으로 자릿수를 맞춘다.
     */
    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(SCORE_SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}
