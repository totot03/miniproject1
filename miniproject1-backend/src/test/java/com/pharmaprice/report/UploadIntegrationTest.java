package com.pharmaprice.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.report.repository.UploadedFileRepository;
import com.pharmaprice.report.service.UploadProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * {@code docs/ROADMAP.md} T-27 완료 판정을 실제 {@code SecurityFilterChain}·DB·
 * 디스크로 검증한다. 415/404는 {@code UploadServiceImplTest}(Mockito)로도 검증되지만,
 * 소유권 기반 403은 {@code ExceptionTranslationFilter}가 실제로 개입해야
 * {@link org.springframework.security.access.AccessDeniedException}이
 * {@code JwtAccessDeniedHandler}로 이어지므로 {@code @WebMvcTest} 슬라이스로는
 * 재현할 수 없다({@code AuthIntegrationTest}가 401/로그아웃을 여기서 검증하는 것과 같은 이유).
 *
 * <p>413(용량 초과)은 여기서 다루지 않는다 - MockMvc의 {@code multipart(...)}는
 * {@code MockHttpServletRequest.getParts()}에 파트를 직접 꽂아줘 Tomcat의 실제 용량
 * 검사를 타지 않는다. 진짜 임베디드 서버가 필요해 {@code UploadMaxSizeIntegrationTest}로
 * 분리했다.</p>
 */
@AutoConfigureMockMvc
class UploadIntegrationTest extends AbstractIntegrationTest {

    /** {@code tools/seed-generator/build_master_seed.py}의 ADMIN_PASSWORD 평문. */
    private static final String ADMIN_PASSWORD = "Admin1234!";

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 1, 2, 3, 4};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private UploadProperties uploadProperties;

    private String signupAndLogin() throws Exception {
        String email = "upload-" + UUID.randomUUID() + "@example.com";
        String password = "Password123";

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"%s","nickname":"업로더"}
                    """.formatted(email, password)))
            .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"%s"}
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();

        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
    }

    private String adminLogin() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"admin@example.com","password":"%s"}
                    """.formatted(ADMIN_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();

        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
    }

    private MockMultipartHttpServletRequestBuilder uploadRequest(String accessToken, MockMultipartFile file) {
        return multipart("/api/v1/uploads")
            .file(file)
            .param("purpose", "RECEIPT")
            .header("Authorization", "Bearer " + accessToken);
    }

    @Test
    void 정상_업로드는_201과_함께_디스크에_파일을_남긴다() throws Exception {
        String accessToken = signupAndLogin();
        var file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES);

        MvcResult result = mockMvc.perform(uploadRequest(accessToken, file))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.originalName").value("receipt.jpg"))
            .andExpect(jsonPath("$.contentType").value("image/jpeg"))
            .andExpect(jsonPath("$.sizeBytes").value(JPEG_BYTES.length))
            .andReturn();

        long fileId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.url"))
            .isEqualTo("/api/v1/uploads/" + fileId);

        String storedPath = uploadedFileRepository.findById(fileId).orElseThrow().getStoredPath();
        Path onDisk = Path.of(uploadProperties.uploadDir()).resolve(storedPath);
        assertThat(onDisk).exists();
    }

    @Test
    void jpg로_위장한_PDF는_415이고_DB에도_디스크에도_남지_않는다() throws Exception {
        String accessToken = signupAndLogin();
        byte[] pdfBytes = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", pdfBytes);

        long countBefore = uploadedFileRepository.count();

        mockMvc.perform(uploadRequest(accessToken, file))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));

        assertThat(uploadedFileRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void 업로더_본인은_자신의_파일을_200으로_조회한다() throws Exception {
        String accessToken = signupAndLogin();
        var file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES);

        MvcResult uploadResult = mockMvc.perform(uploadRequest(accessToken, file))
            .andExpect(status().isCreated())
            .andReturn();
        long fileId = ((Number) JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(get("/api/v1/uploads/" + fileId).header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());
    }

    @Test
    void 타인의_파일을_USER권한으로_조회하면_403() throws Exception {
        String ownerToken = signupAndLogin();
        String strangerToken = signupAndLogin();
        var file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES);

        MvcResult uploadResult = mockMvc.perform(uploadRequest(ownerToken, file))
            .andExpect(status().isCreated())
            .andReturn();
        long fileId = ((Number) JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(get("/api/v1/uploads/" + fileId).header("Authorization", "Bearer " + strangerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void ADMIN은_타인의_파일도_200으로_조회한다() throws Exception {
        String ownerToken = signupAndLogin();
        String adminToken = adminLogin();
        var file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES);

        MvcResult uploadResult = mockMvc.perform(uploadRequest(ownerToken, file))
            .andExpect(status().isCreated())
            .andReturn();
        long fileId = ((Number) JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(get("/api/v1/uploads/" + fileId).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void 존재하지_않는_id를_조회하면_404() throws Exception {
        String accessToken = signupAndLogin();

        mockMvc.perform(get("/api/v1/uploads/999999999").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("UPLOADED_FILE_NOT_FOUND"));
    }

    @Test
    void 토큰없이_조회하면_401() throws Exception {
        mockMvc.perform(get("/api/v1/uploads/1"))
            .andExpect(status().isUnauthorized());
    }
}
