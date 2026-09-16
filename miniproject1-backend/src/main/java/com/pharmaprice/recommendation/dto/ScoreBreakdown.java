package com.pharmaprice.recommendation.dto;

/**
 * 추천 Score 산출 근거. docs/API.md §5 응답의 {@code scoreBreakdown} 형태와 1:1 대응한다.
 *
 * <p>관리자·디버깅용이지만 "순위를 설명할 수 있어야 한다"는 완료 판정 때문에
 * 응답에 항상 채워 넣는다. 세 점수는 이미 소수점 4자리로 반올림된 값이다.</p>
 */
public record ScoreBreakdown(
    double priceScore,
    double distanceScore,
    double freshnessScore,
    Weights weights
) {

    /**
     * {@code RecommendationProperties.Weights} 를 그대로 노출하지 않고 복제한다 —
     * recommendation.dto 패키지가 common.config 패키지에 의존하지 않도록 계층을 분리한다.
     */
    public record Weights(double price, double distance, double freshness) {}
}
