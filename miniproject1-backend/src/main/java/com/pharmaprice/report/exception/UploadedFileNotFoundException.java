package com.pharmaprice.report.exception;

import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;

/**
 * {@code GET /uploads/{fileId}}에서 해당 id의 업로드 파일이 없을 때.
 * {@code GlobalExceptionHandler}가 {@code 404 UPLOADED_FILE_NOT_FOUND}로 변환한다.
 */
public class UploadedFileNotFoundException extends BusinessException {

    public UploadedFileNotFoundException(String message) {
        super(ErrorCode.UPLOADED_FILE_NOT_FOUND, message);
    }
}
