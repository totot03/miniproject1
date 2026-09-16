package com.pharmaprice.report.domain;

/**
 * 가격 제보 출처. {@code price_report.source} 컬럼(VARCHAR(20))에
 * {@code @Enumerated(EnumType.STRING)} 으로 매핑한다.
 *
 * <p>값 집합은 V1__init.sql 의 {@code ck_price_report_source} CHECK 제약과
 * 정확히 일치해야 한다. {@code RECEIPT_OCR}·{@code PARTNER} 는 예약 값으로
 * 현재 범위에서는 실제로 쓰이지 않는다.</p>
 */
public enum ReportSource {
    FORM,
    SEED,
    RECEIPT_OCR,
    PARTNER
}
