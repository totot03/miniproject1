package com.pharmaprice.recommendation.exception;

import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;

/**
 * {@code /search}가 받은 {@code drugId}가 존재하지 않거나 전문의약품일 때 던진다.
 * {@code GlobalExceptionHandler}가 {@code 404 DRUG_NOT_FOUND}로 변환한다.
 *
 * <p>{@code drug.exception.DrugNotFoundException}/{@code report.exception.DrugNotFoundException}와
 * 형태는 같지만, 이 프로젝트는 도메인 간 예외 재사용 선례가 없어 독립된 클래스로 둔다
 * (admin/report의 기존 중복과 같은 이유).</p>
 */
public class DrugNotFoundException extends BusinessException {

    public DrugNotFoundException(String message) {
        super(ErrorCode.DRUG_NOT_FOUND, message);
    }
}
