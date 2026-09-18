package com.pharmaprice.report.exception;

import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;

/**
 * 가격 제보의 {@code purchasedAt}이 미래이거나 오늘로부터 180일을 초과한 과거일 때 던진다.
 * {@code GlobalExceptionHandler}가 {@code 400 INVALID_DATE_RANGE}로 변환한다.
 */
public class InvalidDateRangeException extends BusinessException {

    public InvalidDateRangeException(String message) {
        super(ErrorCode.INVALID_DATE_RANGE, message);
    }
}
