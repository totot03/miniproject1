package com.pharmaprice.report.exception;

/** 매직바이트로 판정한 실제 파일 타입이 image/jpeg·png·webp 중 어느 것도 아닐 때. */
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
