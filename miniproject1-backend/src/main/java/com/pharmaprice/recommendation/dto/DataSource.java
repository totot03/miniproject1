package com.pharmaprice.recommendation.dto;

/**
 * 검색 결과에 포함된 제보들의 출처 구성. docs/API.md §5 응답의 {@code dataSource}
 * 필드에 대응하며, 프론트 고지 배너("학습용 예시 데이터입니다") 노출 여부를
 * 결정하는 신호다.
 */
public enum DataSource {

    /** 결과에 포함된 유효 제보가 전부 시드(SEED)다 */
    SEED,

    /** 시드와 실사용자 제보(FORM 등 SEED 아닌 값)가 섞여 있다 */
    MIXED,

    /** 결과에 포함된 유효 제보가 전부 실사용자 제보다 */
    USER
}
