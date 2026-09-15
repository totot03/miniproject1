package com.pharmaprice.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * DataSource/Flyway를 띄우지 않고 application.yml 의 recommendation.* 값이
 * RecommendationProperties 로 실제로 바인딩되는지만 순수하게 검증한다.
 * ConfigDataApplicationContextInitializer 를 붙여야 application.yml 이 실제로 로드된다
 * (ApplicationContextRunner 는 기본적으로 ConfigData 처리를 하지 않는다).
 */
class RecommendationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RecommendationProperties.class)
    static class TestConfig {}

    @Test
    void bindsRecommendationPropertiesFromApplicationYml() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RecommendationProperties.class);
            RecommendationProperties props = context.getBean(RecommendationProperties.class);

            assertThat(props.weights().price()).isEqualTo(0.60);
            assertThat(props.weights().distance()).isEqualTo(0.25);
            assertThat(props.weights().freshness()).isEqualTo(0.15);
            assertThat(props.freshnessHalfLifeDays()).isEqualTo(30);
            assertThat(props.priceWindowDays()).isEqualTo(90);
            assertThat(props.priceWindowFallbackDays()).isEqualTo(180);
            assertThat(props.outlier().iqrMultiplier()).isEqualTo(1.5);
            assertThat(props.outlier().minSamples()).isEqualTo(4);
        });
    }
}
