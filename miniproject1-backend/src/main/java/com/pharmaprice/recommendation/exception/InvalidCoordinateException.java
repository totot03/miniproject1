package com.pharmaprice.recommendation.exception;

import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;

/**
 * 사용자가 입력한 위경도가 대한민국 범위(위도 33~39, 경도 124~132) 밖일 때 던진다.
 * {@code DistanceCalculator.validateCoordinate}가 던지고, {@code GlobalExceptionHandler}가
 * {@code 400 INVALID_COORDINATE}로 변환한다.
 */
public class InvalidCoordinateException extends BusinessException {

    public InvalidCoordinateException(String message) {
        super(ErrorCode.INVALID_COORDINATE, message);
    }
}
