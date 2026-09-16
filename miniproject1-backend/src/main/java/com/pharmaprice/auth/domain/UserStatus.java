package com.pharmaprice.auth.domain;

/**
 * 사용자 상태. {@code app_user.status} 컬럼(VARCHAR(20))에
 * {@code @Enumerated(EnumType.STRING)} 으로 매핑한다.
 *
 * <p>값 집합은 V1__init.sql 의 {@code ck_app_user_status} CHECK 제약과
 * 정확히 일치해야 한다.</p>
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED
}
