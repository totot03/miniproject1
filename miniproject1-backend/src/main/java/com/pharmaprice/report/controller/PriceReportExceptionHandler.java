package com.pharmaprice.report.controller;

import com.pharmaprice.common.dto.ErrorResponse;
import com.pharmaprice.report.exception.DrugNotFoundException;
import com.pharmaprice.report.exception.DrugNotOtcException;
import com.pharmaprice.report.exception.InvalidDateRangeException;
import com.pharmaprice.report.exception.PharmacyNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code report.controller} 패키지에만 적용되는 예외 처리기.
 *
 * <p>{@code AuthExceptionHandler}와 동일한 이유로 존재한다 - 프로젝트 전체 공용
 * {@code @RestControllerAdvice}가 아직 없어({@code docs/ROADMAP.md} T-35 몫) T-26
 * 완료 판정이 요구하는 정확한 코드를 지금 맞추려면 이 범위만 좁힌 처리기가 필요하다.
 * {@code basePackages}로 범위를 좁혀 T-35의 전역 처리기와 매칭 범위가 겹치지 않게 한다.</p>
 *
 * <p>{@code DataIntegrityViolationException}은 별도 커스텀 예외를 만들지 않고 여기서
 * 직접 잡는다 - {@code uq_report_user_pair_day} 부분 유니크 인덱스 위반이 유일한 발생
 * 경로이므로({@code docs/DATABASE.md} §3.5) 항상 중복 제보로 해석해도 안전하다.</p>
 */
@RestControllerAdvice(basePackages = "com.pharmaprice.report.controller")
class PriceReportExceptionHandler {

    @ExceptionHandler(PharmacyNotFoundException.class)
    ResponseEntity<ErrorResponse> handlePharmacyNotFound(PharmacyNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("PHARMACY_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(DrugNotFoundException.class)
    ResponseEntity<ErrorResponse> handleDrugNotFound(DrugNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("DRUG_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(DrugNotOtcException.class)
    ResponseEntity<ErrorResponse> handleDrugNotOtc(DrugNotOtcException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse.of("DRUG_NOT_OTC", e.getMessage()));
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    ResponseEntity<ErrorResponse> handleInvalidDateRange(InvalidDateRangeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("INVALID_DATE_RANGE", e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleDuplicateReport(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("DUPLICATE_REPORT", "이미 같은 날 같은 약국·약품으로 제보하셨습니다."));
    }
}
