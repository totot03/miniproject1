package com.pharmaprice.common.exception;

/**
 * 요청 파라미터 조합이 잘못됐을 때 던지는 범용 예외 — Bean Validation(`@Valid`)으로
 * 표현할 수 없는, 여러 선택적 쿼리 파라미터 사이의 "이 중 하나는 필수" 같은 조합
 * 규칙 위반에 쓴다(예: {@code /pharmacies}의 "q 또는 lat/lng 중 하나는 필수",
 * {@code /search}의 "lat/lng 또는 regionCode 중 하나는 필수"). 단일 필드 검증이
 * 아니라 도메인 개념도 아니라서, 다른 도메인 예외처럼 각 패키지에 따로 두지 않고
 * {@code common}에 하나만 둔다. {@code GlobalExceptionHandler}가
 * {@code 400 VALIDATION_FAILED}로 변환한다.
 */
public class InvalidRequestException extends BusinessException {

    public InvalidRequestException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
