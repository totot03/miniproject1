package com.pharmaprice.drug.exception;

import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;

/**
 * 약품 상세 조회 시 요청한 {@code drugId}가 존재하지 않을 때 던진다.
 * {@code GlobalExceptionHandler}가 {@code 404 DRUG_NOT_FOUND}로 변환한다.
 */
public class DrugNotFoundException extends BusinessException {

    public DrugNotFoundException(String message) {
        super(ErrorCode.DRUG_NOT_FOUND, message);
    }
}
