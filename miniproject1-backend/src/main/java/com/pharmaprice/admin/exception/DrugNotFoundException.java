package com.pharmaprice.admin.exception;

/**
 * 관리자 통계 조회 시 요청한 {@code drugId}가 존재하지 않을 때 던진다.
 * {@code AdminStatsExceptionHandler}가 {@code 404 DRUG_NOT_FOUND}로 변환한다.
 *
 * <p>{@code report.exception.DrugNotFoundException}과 형태는 같지만, 이 프로젝트는
 * 도메인 간 예외 재사용 선례가 없어(각 도메인이 자기 exception 패키지를 둔다)
 * 독립된 클래스로 둔다.</p>
 */
public class DrugNotFoundException extends RuntimeException {

    public DrugNotFoundException(String message) {
        super(message);
    }
}
