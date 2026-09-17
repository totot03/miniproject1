package com.pharmaprice.report.controller;

import com.pharmaprice.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * {@code MaxUploadSizeExceededException}(멀티파트 용량 초과)을 {@code docs/API.md} §1.2
 * 포맷의 {@code 413 FILE_TOO_LARGE}로 변환한다.
 *
 * <p>이 예외는 {@code DispatcherServlet}이 핸들러를 찾기 전 멀티파트를 파싱하는
 * 단계에서 던져지므로, 예외가 처리될 시점엔 아직 handlerMethod가 없다(null).
 * {@code ExceptionHandlerExceptionResolver}가 어드바이스를 고를 때 쓰는
 * {@code HandlerTypePredicate}는 대상 타입이 null이면, {@code basePackages} 등
 * 셀렉터가 하나라도 있는 어드바이스는 전부 걸러버린다(항상 false). 즉
 * {@code UploadExceptionHandler}처럼 {@code basePackages}로 스코프를 좁힌
 * 어드바이스로는 이 예외를 절대 잡을 수 없다 - 셀렉터가 전혀 없는 완전 전역
 * {@code @RestControllerAdvice}가 따로 있어야 한다. 그래서 이 클래스만
 * {@code basePackages} 없이 프로젝트 전체에 적용된다.</p>
 *
 * <p>T-35(전역 예외 처리기 도입)에서 진짜 전역 처리기를 만들 때 이 클래스를
 * 흡수하거나 참고할 것.</p>
 */
@RestControllerAdvice
class MultipartUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
            .body(ErrorResponse.of("FILE_TOO_LARGE", "파일 크기는 5MB를 초과할 수 없습니다."));
    }
}
