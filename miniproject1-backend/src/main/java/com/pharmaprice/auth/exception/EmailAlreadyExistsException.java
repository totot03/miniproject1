package com.pharmaprice.auth.exception;

import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;

/**
 * 회원가입 시 이미 존재하는 이메일로 가입을 시도했을 때 던진다.
 * {@code GlobalExceptionHandler}가 {@code 409 EMAIL_ALREADY_EXISTS}로 변환한다.
 */
public class EmailAlreadyExistsException extends BusinessException {

    public EmailAlreadyExistsException(String message) {
        super(ErrorCode.EMAIL_ALREADY_EXISTS, message);
    }
}
