package com.pharmaprice.report.domain;

/**
 * 이상치 자동 탐지 사유. {@code price_report.flag_reason} 컬럼(VARCHAR(100))에
 * {@code @Enumerated(EnumType.STRING)} 으로 매핑한다. NULL 을 허용하는
 * 선택 컬럼이라, 다른 enum 컬럼들과 달리 CHECK 제약도
 * {@code flag_reason IS NULL OR flag_reason IN (...)} 형태다.
 *
 * <p>값 집합은 V1__init.sql 의 {@code ck_price_report_flag_reason} CHECK
 * 제약과 정확히 일치해야 한다.</p>
 */
public enum FlagReason {
    OUTLIER_HIGH,
    OUTLIER_LOW,
    DUPLICATE,
    MANUAL
}
