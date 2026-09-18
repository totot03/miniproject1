package com.pharmaprice.admin.controller;

import com.pharmaprice.admin.exception.DrugNotFoundException;
import com.pharmaprice.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code admin.controller} 패키지에만 적용되는 예외 처리기. {@code report.controller.PriceReportExceptionHandler}와
 * 동일한 이유로 존재한다 - 프로젝트 전체 공용 {@code @RestControllerAdvice}가 아직 없다(T-35 몫).
 */
@RestControllerAdvice(basePackages = "com.pharmaprice.admin.controller")
class AdminStatsExceptionHandler {

    @ExceptionHandler(DrugNotFoundException.class)
    ResponseEntity<ErrorResponse> handleDrugNotFound(DrugNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("DRUG_NOT_FOUND", e.getMessage()));
    }
}
