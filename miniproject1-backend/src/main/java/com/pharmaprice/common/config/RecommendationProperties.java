package com.pharmaprice.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 recommendation.* 설정을 바인딩한다.
 * Score(추천 점수) 계산에 쓰이는 가중치와 이상치 제거 기준을 담는다.
 */
@ConfigurationProperties(prefix = "recommendation")
public record RecommendationProperties(
    Weights weights,
    int freshnessHalfLifeDays,
    int priceWindowDays,
    int priceWindowFallbackDays,
    Outlier outlier
) {
    public record Weights(double price, double distance, double freshness) {}

    public record Outlier(double iqrMultiplier, int minSamples) {}
}
