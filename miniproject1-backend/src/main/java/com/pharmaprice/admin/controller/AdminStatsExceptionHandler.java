package com.pharmaprice.admin.controller;

import com.pharmaprice.admin.exception.DrugNotFoundException;
import com.pharmaprice.admin.exception.ReportNotFoundException;
import com.pharmaprice.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code admin.controller} 패키지 전체({@code basePackages}로 스코프)에 적용되는
 * 예외 처리기 - {@code AdminStatsController}뿐 아니라 T-32의 {@code AdminReportController}도
 * 이미 이 스코프 안에 있어 별도 advice 클래스를 만들지 않고 여기에 핸들러만
 * 추가한다({@code report.controller.PriceReportExceptionHandler}와 동일한 존재 이유 -
 * 프로젝트 전체 공용 {@code @RestControllerAdvice}는 아직 없다, T-35 몫).
 */
@RestControllerAdvice(basePackages = "com.pharmaprice.admin.controller")
class AdminStatsExceptionHandler {

    @ExceptionHandler(DrugNotFoundException.class)
    ResponseEntity<ErrorResponse> handleDrugNotFound(DrugNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("DRUG_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ReportNotFoundException.class)
    ResponseEntity<ErrorResponse> handleReportNotFound(ReportNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("REPORT_NOT_FOUND", e.getMessage()));
    }
}
