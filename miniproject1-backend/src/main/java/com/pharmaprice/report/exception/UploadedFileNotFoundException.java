package com.pharmaprice.report.exception;

/** {@code GET /uploads/{fileId}} 에서 해당 id의 업로드 파일이 없을 때. */
public class UploadedFileNotFoundException extends RuntimeException {

    public UploadedFileNotFoundException(String message) {
        super(message);
    }
}
