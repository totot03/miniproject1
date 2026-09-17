package com.pharmaprice.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * {@code docs/ROADMAP.md} T-27 "5MB 초과 -> 413 FILE_TOO_LARGE" 검증.
 *
 * <p>MockMvc의 {@code multipart(...)}는 {@code MockHttpServletRequest.getParts()}에
 * 파트를 직접 꽂아줘 Tomcat의 실제 멀티파트 용량 검사를 타지 않는다 - 이 시나리오는
 * 진짜 임베디드 서버가 요청을 파싱해야만 재현된다. 그래서 {@code AbstractIntegrationTest}
 * (기본 MOCK 웹 환경)를 상속하지 않고 {@code RANDOM_PORT}로 별도 서버를 띄운다.
 * 트랜잭션 롤백도 의미가 없다 - 실제 HTTP 요청은 별도 스레드/커넥션에서 처리된다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class UploadMaxSizeIntegrationTest {

    /** {@code tools/seed-generator/build_master_seed.py}의 ADMIN_PASSWORD 평문. */
    private static final String ADMIN_PASSWORD = "Admin1234!";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void 파일이_5MB를_초과하면_413_FILE_TOO_LARGE() {
        String accessToken = adminLogin();

        byte[] oversized = new byte[5 * 1024 * 1024 + 1024]; // 5MB + 1KB
        Arrays.fill(oversized, (byte) 1);
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(oversized) {
            @Override
            public String getFilename() {
                return "big.jpg";
            }
        });
        body.add("purpose", "RECEIPT");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(accessToken);

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/uploads", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).contains("\"code\":\"FILE_TOO_LARGE\"");
    }

    private String adminLogin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String requestBody = """
            {"email":"admin@example.com","password":"%s"}
            """.formatted(ADMIN_PASSWORD);

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/login", new HttpEntity<>(requestBody, headers), String.class);

        return JsonPath.read(response.getBody(), "$.accessToken");
    }
}
