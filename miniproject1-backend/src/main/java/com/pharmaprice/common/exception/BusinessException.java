package com.pharmaprice.common.exception;

/**
 * 도메인 예외의 공통 베이스. 각 하위 클래스는 자신의 {@link ErrorCode}를 생성자에서
 * 고정해 던지고, {@code GlobalExceptionHandler}는 이 타입 하나만 잡아 상태 코드·`code`
 * 필드를 {@link ErrorCode}에서 그대로 꺼내 응답한다({@code docs/ROADMAP.md} T-35 2번
 * "커스텀 BusinessException → 각자의 상태·코드").
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
