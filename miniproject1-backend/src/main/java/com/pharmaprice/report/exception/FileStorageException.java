package com.pharmaprice.report.exception;

/**
 * 파일 저장·조회 중 예상치 못한 I/O 실패. REST 코드로 매핑하지 않고 500으로 흘려보낸다
 * - {@code FileStorageService} 계약에 {@code java.io.IOException}을 노출하지 않기 위한 래퍼다.
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
