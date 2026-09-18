package com.pharmaprice.pharmacy.exception;

import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;

/**
 * 약국 상세 조회 시 요청한 {@code pharmacyId}가 존재하지 않거나 비활성일 때 던진다.
 * {@code GlobalExceptionHandler}가 {@code 404 PHARMACY_NOT_FOUND}로 변환한다.
 */
public class PharmacyNotFoundException extends BusinessException {

    public PharmacyNotFoundException(String message) {
        super(ErrorCode.PHARMACY_NOT_FOUND, message);
    }
}
