package com.pharmaprice.report.service;

import java.util.Optional;

/**
 * 파일 내용의 매직바이트로 실제 이미지 타입을 판정한다.
 *
 * <p>클라이언트가 보낸 파일명 확장자나 {@code Content-Type} 헤더는 신뢰하지 않는다
 * ({@code docs/ROADMAP.md} T-27 "확장자가 아니라 실제 매직 바이트로 판정한다") -
 * {@code .jpg}로 위장한 PDF 같은 스푸핑을 막기 위함이다.</p>
 */
public enum DetectedImageType {

    JPEG("image/jpeg", "jpg") {
        @Override
        boolean matches(byte[] content) {
            return startsWith(content, 0, 0xFF, 0xD8, 0xFF);
        }
    },
    PNG("image/png", "png") {
        @Override
        boolean matches(byte[] content) {
            return startsWith(content, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
        }
    },
    WEBP("image/webp", "webp") {
        @Override
        boolean matches(byte[] content) {
            // RIFF????WEBP - 4~7바이트는 파일 크기(리틀 엔디안)라 검사하지 않는다.
            return startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P');
        }
    };

    private final String contentType;
    private final String extension;

    DetectedImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    abstract boolean matches(byte[] content);

    public static Optional<DetectedImageType> detect(byte[] content) {
        for (DetectedImageType type : values()) {
            if (type.matches(content)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, int... expectedBytes) {
        if (content.length < offset + expectedBytes.length) {
            return false;
        }
        for (int i = 0; i < expectedBytes.length; i++) {
            if ((content[offset + i] & 0xFF) != expectedBytes[i]) {
                return false;
            }
        }
        return true;
    }
}
