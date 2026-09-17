package com.pharmaprice.report.controller;

import com.pharmaprice.common.dto.ErrorResponse;
import com.pharmaprice.report.exception.UnsupportedFileTypeException;
import com.pharmaprice.report.exception.UploadedFileNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code report.controller} 패키지에만 적용되는 업로드 예외 처리기.
 *
 * <p>{@code PriceReportExceptionHandler}와 같은 이유로 존재한다 - 전역
 * {@code @RestControllerAdvice}가 아직 없다({@code docs/ROADMAP.md} T-35 몫).
 * {@code 413 FILE_TOO_LARGE}(멀티파트 용량 초과)는 여기서 다루지 않는다 -
 * {@link MultipartUploadExceptionHandler}의 자바독 참고.</p>
 */
@RestControllerAdvice(basePackages = "com.pharmaprice.report.controller")
class UploadExceptionHandler {

    @ExceptionHandler(UnsupportedFileTypeException.class)
    ResponseEntity<ErrorResponse> handleUnsupportedFileType(UnsupportedFileTypeException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(ErrorResponse.of("UNSUPPORTED_FILE_TYPE", e.getMessage()));
    }

    @ExceptionHandler(UploadedFileNotFoundException.class)
    ResponseEntity<ErrorResponse> handleUploadedFileNotFound(UploadedFileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("UPLOADED_FILE_NOT_FOUND", e.getMessage()));
    }
}
