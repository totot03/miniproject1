package com.pharmaprice.report.domain;

/**
 * 가격 제보 상태. {@code price_report.status} 컬럼(VARCHAR(20))에
 * {@code @Enumerated(EnumType.STRING)} 으로 매핑한다.
 *
 * <p>값 집합은 V1__init.sql 의 {@code ck_price_report_status} CHECK 제약과
 * 정확히 일치해야 한다. 중복 제보 방지 인덱스({@code uq_report_user_pair_day})가
 * {@code status = 'ACTIVE'} 조건에 의존하므로 오타에 특히 취약하다.</p>
 */
public enum ReportStatus {
    ACTIVE,
    HIDDEN,
    REJECTED
}
