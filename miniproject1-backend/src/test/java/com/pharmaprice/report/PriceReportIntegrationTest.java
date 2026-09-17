package com.pharmaprice.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.support.TestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code docs/ROADMAP.md} T-26 완료 판정 6가지를 실제 {@code pharmaprice_test} DB로
 * 검증한다. {@link com.pharmaprice.report.service.PriceReportServiceImplTest}(Mockito)로는
 * 중앙값 native 쿼리, DB 부분 유니크 인덱스 위반, {@code PriceStatService.recalculate}의
 * 실제 재계산 결과를 확인할 수 없어 이 통합 테스트가 유일한 검증 지점이다.
 *
 * <p>PostgreSQL은 트랜잭션 안에서 SQL 오류(유니크 인덱스 위반 등)가 나면 그 트랜잭션
 * 전체가 abort 상태가 되어 이후 어떤 쿼리도 실패한다. {@code AbstractIntegrationTest}가
 * 테스트당 트랜잭션 하나를 열고 끝에서만 롤백하는 구조라, 중복 제보 테스트에서는
 * 409를 확인한 뒤 같은 트랜잭션에서 추가 DB 조회를 하지 않는다.</p>
 */
@AutoConfigureMockMvc
class PriceReportIntegrationTest extends AbstractIntegrationTest {

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

    /** signup → login까지 마친 뒤 accessToken을 반환한다({@code AuthIntegrationTest}와 동일한 절차). */
    private String signupAndLogin() throws Exception {
        String email = "report-" + UUID.randomUUID() + "@example.com";
        String password = "Password123";

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"%s","nickname":"제보자"}
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

    private MockHttpServletRequestBuilder reportRequest(String accessToken, long pharmacyId, long drugId, int price) {
        return post("/api/v1/price-reports")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"pharmacyId":%d,"drugId":%d,"price":%d}
                """.formatted(pharmacyId, drugId, price));
    }

    @Test
    void 정상제보는_201과_updatedStat을반환한다() throws Exception {
        String accessToken = signupAndLogin();

        mockMvc.perform(reportRequest(accessToken, pharmacy.getId(), drug.getId(), 2800))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.flagged").value(false))
            .andExpect(jsonPath("$.updatedStat.repPrice").value(2800))
            .andExpect(jsonPath("$.updatedStat.reportCount").value(1));
    }

    @Test
    void 같은날같은약국약품재제보는_409_DUPLICATE_REPORT() throws Exception {
        String accessToken = signupAndLogin();

        mockMvc.perform(reportRequest(accessToken, pharmacy.getId(), drug.getId(), 2800))
            .andExpect(status().isCreated());

        mockMvc.perform(reportRequest(accessToken, pharmacy.getId(), drug.getId(), 3000))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_REPORT"));
    }

    @Test
    void 전문의약품제보시도는_422_DRUG_NOT_OTC() throws Exception {
        String accessToken = signupAndLogin();
        Drug prescriptionDrug = drugRepository.save(Drug.builder()
            .name("전문의약품").displayName("전문의약품").category("기타").packageUnit("1정")
            .otcFlag(false).build());

        mockMvc.perform(reportRequest(accessToken, pharmacy.getId(), prescriptionDrug.getId(), 5000))
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.code").value("DRUG_NOT_OTC"));
    }

    @Test
    void 이상치제보는_201과_flagged_warning을반환하고_rep_price는변하지않는다() throws Exception {
        String normalUserToken = signupAndLogin();
        String outlierUserToken = signupAndLogin();

        // 정상가 1건으로 약품 전체 중앙값(2800)을 형성한다.
        mockMvc.perform(reportRequest(normalUserToken, pharmacy.getId(), drug.getId(), 2800))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.updatedStat.repPrice").value(2800));

        // 2800*3=8400 초과이므로 OUTLIER_HIGH로 플래그돼야 한다.
        mockMvc.perform(reportRequest(outlierUserToken, pharmacy.getId(), drug.getId(), 50_000))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.flagged").value(true))
            .andExpect(jsonPath("$.flagReason").value("OUTLIER_HIGH"))
            .andExpect(jsonPath("$.warning").isNotEmpty())
            // 이상치는 통계 계산에서 제외되므로 대표가격·건수가 정상가 1건 기준 그대로여야 한다.
            .andExpect(jsonPath("$.updatedStat.repPrice").value(2800))
            .andExpect(jsonPath("$.updatedStat.reportCount").value(1));
    }

    @Test
    void 제보직후_search결과에즉시반영된다() throws Exception {
        String accessToken = signupAndLogin();

        mockMvc.perform(reportRequest(accessToken, pharmacy.getId(), drug.getId(), 2800))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/search")
                .param("drugId", String.valueOf(drug.getId()))
                .param("lat", String.valueOf(pharmacy.getLat()))
                .param("lng", String.valueOf(pharmacy.getLng())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].pharmacy.id").value(pharmacy.getId()))
            .andExpect(jsonPath("$.results[0].price.repPrice").value(2800));
    }

    @Test
    void 제보후_authMe의_reportCount가1증가한다() throws Exception {
        String accessToken = signupAndLogin();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
            .andExpect(jsonPath("$.reportCount").value(0));

        mockMvc.perform(reportRequest(accessToken, pharmacy.getId(), drug.getId(), 2800))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
            .andExpect(jsonPath("$.reportCount").value(1));
    }
}
