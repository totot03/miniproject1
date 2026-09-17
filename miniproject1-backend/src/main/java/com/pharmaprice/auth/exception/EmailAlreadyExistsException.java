package com.pharmaprice.auth.exception;

/**
 * 회원가입 시 이미 존재하는 이메일로 가입을 시도했을 때 던진다.
 * {@code AuthExceptionHandler} 가 {@code 409 EMAIL_ALREADY_EXISTS} 로 변환한다.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
