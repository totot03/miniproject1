package com.pharmaprice.auth.controller;

import com.pharmaprice.auth.exception.AuthenticationFailedException;
import com.pharmaprice.auth.exception.EmailAlreadyExistsException;
import com.pharmaprice.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code auth.controller} 패키지에만 적용되는 예외 처리기.
 *
 * <p>프로젝트 전체에는 아직 {@code @RestControllerAdvice}가 없다({@code docs/ROADMAP.md}
 * T-35 몫). signup의 409·login/refresh의 401은 보안에 직결되고 완료 판정이 정확한
 * 상태 코드를 요구해서, T-35를 기다리지 않고 이 패키지에만 한정된 작은 처리기로
 * {@code docs/API.md} §1.2 포맷을 지금 맞춘다. {@code basePackages}로 범위를 좁혀
 * T-35가 전역 처리기를 만들 때 매칭 범위가 겹치지 않게 한다.</p>
 */
@RestControllerAdvice(basePackages = "com.pharmaprice.auth.controller")
class AuthExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("EMAIL_ALREADY_EXISTS", e.getMessage()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ErrorResponse> handleAuthenticationFailed(AuthenticationFailedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.of("UNAUTHENTICATED", e.getMessage()));
    }
}
