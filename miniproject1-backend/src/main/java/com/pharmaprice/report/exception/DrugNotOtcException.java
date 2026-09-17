package com.pharmaprice.report.exception;

/**
 * 가격 제보 생성 시 요청한 약품이 일반의약품이 아닐 때({@code otc_flag = false}) 던진다.
 * {@code PriceReportExceptionHandler}가 {@code 422 DRUG_NOT_OTC}로 변환한다.
 */
public class DrugNotOtcException extends RuntimeException {

    public DrugNotOtcException(String message) {
        super(message);
    }
}
