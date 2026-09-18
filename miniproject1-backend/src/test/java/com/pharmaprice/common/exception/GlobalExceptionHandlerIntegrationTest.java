package com.pharmaprice.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code docs/ROADMAP.md} T-35 완료 판정을 실제 HTTP 왕복으로 검증한다.
 *
 * <p>좌표·반경 케이스는 회귀 테스트다 — {@code GlobalExceptionHandler} 도입 전에는
 * {@code IllegalArgumentException}이 그대로 흘러나가 500이 났다([[local-db-and-test-environment]]
 * T-19 실측 기록). 지금 이 테스트가 400을 기대하는 것 자체가 그 버그가 고쳐졌다는 증거다.</p>
 */
@AutoConfigureMockMvc
class GlobalExceptionHandlerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private DrugRepository drugRepository;

    private Pharmacy pharmacy;
    private Drug drug;

    @BeforeEach
    void setUpFixtures() {
        Region region = regionRepository.save(TestFixtures.region());
        pharmacy = pharmacyRepository.save(TestFixtures.pharmacy(region));
        drug = drugRepository.save(TestFixtures.drug());
    }

    @Test
    void 회원가입_검증실패는_400_VALIDATION_FAILED와_한글_fieldErrors를반환한다() throws Exception {
        // password="ab1"은 Size(8~64)만 위반한다(Pattern은 통과) — 필드당 위반 하나로
        // 맞춰야 jsonPath 필터가 값 하나로 unwrap된다. nickname="a"는 Size 위반이지만
        // 검증하지 않으므로 무관하다.
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"이메일아님","password":"ab1","nickname":"a"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.traceId").isNotEmpty())
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')].reason")
                .value("올바른 이메일 형식이 아닙니다."))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')].reason")
                .value("8자 이상 64자 이하로 입력해 주세요."));
    }

    @Test
    void 가격이범위밖이면_400이고_reason이API문서예시와정확히같다() throws Exception {
        String accessToken = signupAndLogin();

        mockMvc.perform(post("/api/v1/price-reports")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"pharmacyId":%d,"drugId":%d,"price":50}
                    """.formatted(pharmacy.getId(), drug.getId())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("price"))
            .andExpect(jsonPath("$.fieldErrors[0].reason").value("가격은 100원 이상 200,000원 이하여야 합니다."));
    }

    @Test
    void 검색좌표가대한민국범위밖이면_500이아니라400_INVALID_COORDINATE() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                .param("drugId", String.valueOf(drug.getId()))
                .param("lat", "1.0")
                .param("lng", "1.0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_COORDINATE"));
    }

    @Test
    void 허용안된반경이면_500이아니라400_INVALID_RADIUS() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                .param("drugId", String.valueOf(drug.getId()))
                .param("lat", String.valueOf(pharmacy.getLat()))
                .param("lng", String.valueOf(pharmacy.getLng()))
                .param("radius", "999"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_RADIUS"));
    }

    @Test
    void 존재하지않는약국으로제보하면_404_PHARMACY_NOT_FOUND() throws Exception {
        String accessToken = signupAndLogin();

        mockMvc.perform(post("/api/v1/price-reports")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"pharmacyId":999999,"drugId":%d,"price":2800}
                    """.formatted(drug.getId())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PHARMACY_NOT_FOUND"));
    }

    /** signup → login까지 마친 뒤 accessToken을 반환한다({@code PriceReportIntegrationTest}와 동일한 절차). */
    private String signupAndLogin() throws Exception {
        String email = "exc-" + java.util.UUID.randomUUID() + "@example.com";
        String password = "Password123";

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"%s","nickname":"제보자"}
                    """.formatted(email, password)))
            .andExpect(status().isCreated());

        var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"%s"}
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();

        return com.jayway.jsonpath.JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
    }
}
