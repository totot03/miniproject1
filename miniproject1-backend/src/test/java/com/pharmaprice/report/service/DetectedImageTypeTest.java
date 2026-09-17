package com.pharmaprice.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@code docs/ROADMAP.md} T-27 "확장자가 아니라 실제 매직 바이트로 판정한다" 검증.
 * 클라이언트가 보낸 파일명·Content-Type과 무관하게 바이트 내용만으로 판정해야 한다.
 */
class DetectedImageTypeTest {

    @Test
    void jpeg매직바이트를_정확히_판정한다() {
        byte[] content = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

        Optional<DetectedImageType> detected = DetectedImageType.detect(content);

        assertThat(detected).contains(DetectedImageType.JPEG);
        assertThat(detected.get().contentType()).isEqualTo("image/jpeg");
        assertThat(detected.get().extension()).isEqualTo("jpg");
    }

    @Test
    void png매직바이트를_정확히_판정한다() {
        byte[] content = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0
        };

        Optional<DetectedImageType> detected = DetectedImageType.detect(content);

        assertThat(detected).contains(DetectedImageType.PNG);
    }

    @Test
    void webp매직바이트를_정확히_판정한다() {
        byte[] content = new byte[16];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, content, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, content, 8, 4);

        Optional<DetectedImageType> detected = DetectedImageType.detect(content);

        assertThat(detected).contains(DetectedImageType.WEBP);
    }

    @Test
    void jpg로_위장한_PDF는_판정되지_않는다() {
        byte[] pdfContent = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

        Optional<DetectedImageType> detected = DetectedImageType.detect(pdfContent);

        assertThat(detected).isEmpty();
    }

    @Test
    void 너무_짧은_바이트배열은_예외없이_미판정으로_처리한다() {
        byte[] tooShort = {0x01, 0x02};

        assertThat(DetectedImageType.detect(tooShort)).isEmpty();
    }

    @Test
    void 빈_바이트배열은_미판정으로_처리한다() {
        assertThat(DetectedImageType.detect(new byte[0])).isEmpty();
    }
}
