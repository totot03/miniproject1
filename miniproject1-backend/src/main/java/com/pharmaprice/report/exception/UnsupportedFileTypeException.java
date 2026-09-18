package com.pharmaprice.report.exception;

import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;

/**
 * 매직바이트로 판정한 실제 파일 타입이 image/jpeg·png·webp 중 어느 것도 아닐 때.
 * {@code GlobalExceptionHandler}가 {@code 415 UNSUPPORTED_FILE_TYPE}으로 변환한다.
 */
public class UnsupportedFileTypeException extends BusinessException {

    public UnsupportedFileTypeException(String message) {
        super(ErrorCode.UNSUPPORTED_FILE_TYPE, message);
    }
}
