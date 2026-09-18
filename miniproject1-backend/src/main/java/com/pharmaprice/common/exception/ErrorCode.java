package com.pharmaprice.common.exception;

import org.springframework.http.HttpStatus;

/**
 * {@code docs/API.md} §1.3 에러 코드 표를 그대로 옮긴다. 코드와 문서가 어긋나지 않도록
 * 이 enum이 유일한 출처(source of truth)다 — 새 에러 상황이 생기면 여기부터 추가한다.
 *
 * <p>{@link #UPLOADED_FILE_NOT_FOUND}는 §1.3 표에는 없지만 {@code GET /uploads/{fileId}}가
 * 이미 이 코드로 404를 내려주고 있어(T-27) 기존 동작을 유지하기 위해 남겨둔다.</p>
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_COORDINATE(HttpStatus.BAD_REQUEST),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST),
    INVALID_RADIUS(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    PHARMACY_NOT_FOUND(HttpStatus.NOT_FOUND),
    DRUG_NOT_FOUND(HttpStatus.NOT_FOUND),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND),
    UPLOADED_FILE_NOT_FOUND(HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    DUPLICATE_REPORT(HttpStatus.CONFLICT),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    DRUG_NOT_OTC(HttpStatus.UNPROCESSABLE_ENTITY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
