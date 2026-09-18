package com.pharmaprice.recommendation.exception;

import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;

/**
 * {@code /search}가 받은 {@code radius}가 허용값(500/1000/2000/5000) 밖일 때 던진다.
 * {@code GlobalExceptionHandler}가 {@code 400 INVALID_RADIUS}로 변환한다.
 *
 * <p>{@code HaversineDistanceCalculator.boundingBox}의 {@code radiusM <= 0} 방어 체크와는
 * 다르다 — 그건 호출자의 프로그래밍 오류를 잡는 내부 불변식이라 그대로
 * {@code IllegalArgumentException}(→500)으로 남긴다. 이 예외는 사용자가 API로 보낸
 * {@code radius} 쿼리 파라미터 검증 실패만을 위한 것이다.</p>
 */
public class InvalidRadiusException extends BusinessException {

    public InvalidRadiusException(String message) {
        super(ErrorCode.INVALID_RADIUS, message);
    }
}
