package com.pharmaprice.admin.exception;

import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;

/**
 * 관리자가 존재하지 않는 {@code reportId}를 PATCH하려 할 때 던진다.
 * {@code GlobalExceptionHandler}가 {@code 404 REPORT_NOT_FOUND}로 변환한다.
 *
 * <p>{@code DrugNotFoundException}과 동일한 이유로 report.exception 패키지의
 * 예외를 재사용하지 않고 admin.exception에 독립된 클래스를 둔다.</p>
 */
public class ReportNotFoundException extends BusinessException {

    public ReportNotFoundException(String message) {
        super(ErrorCode.REPORT_NOT_FOUND, message);
    }
}
