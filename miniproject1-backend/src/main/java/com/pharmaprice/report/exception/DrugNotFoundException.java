package com.pharmaprice.report.exception;

/**
 * 가격 제보 생성 시 요청한 {@code drugId}가 존재하지 않을 때 던진다.
 * {@code PriceReportExceptionHandler}가 {@code 404 DRUG_NOT_FOUND}로 변환한다.
 */
public class DrugNotFoundException extends RuntimeException {

    public DrugNotFoundException(String message) {
        super(message);
    }
}
