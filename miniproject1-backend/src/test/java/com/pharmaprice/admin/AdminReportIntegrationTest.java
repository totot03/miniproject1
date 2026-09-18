package com.pharmaprice.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.auth.security.JwtTokenProvider;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.report.domain.FlagReason;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportStatus;
import com.pharmaprice.report.repository.PriceReportRepository;
import com.pharmaprice.support.TestFixtures;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code docs/ROADMAP.md} T-32 완료 판정과 {@code docs/API.md} §8 제보 관리 명세를
 * 실제 {@code pharmaprice_test} DB로 검증한다. {@code AdminReportServiceImplTest}
 * (있다면 Mockito 기반)로는 {@code PriceStatRepository.aggregate} native 쿼리의
 * 실제 재계산 결과나 {@code saveAndFlush} 없이 재계산을 호출했을 때 생기는 stale-read
 * 버그를 잡을 수 없어, 이 통합 테스트가 유일한 검증 지점이다({@code AdminStatsIntegrationTest}
 * 와 동일한 위치의 테스트).
 *
 * <p>제보는 {@code user_id}를 비워두면(seed 데이터와 동일하게) {@code uq_report_user_pair_day}
 * 부분 유니크 인덱스(조건: {@code user_id IS NOT NULL AND status = 'ACTIVE'})의
 * 적용을 받지 않는다 - 그래서 대부분의 시나리오는 {@code PriceReport.builder()}로
 * 직접 여러 건을 자유롭게 심을 수 있다. report_count 차감 시나리오만 실제
 * {@code AppUser}를 필요로 한다.</p>
 */
@AutoConfigureMockMvc
class AdminReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private DrugRepository drugRepository;

    @Autowired
    private PriceReportRepository priceReportRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    private Pharmacy pharmacy;
    private Drug drug;

    @BeforeEach
    void setUpFixtures() {
        Region region = regionRepository.save(TestFixtures.region());
        pharmacy = pharmacyRepository.save(TestFixtures.pharmacy(region));
        drug = drugRepository.save(TestFixtures.drug());
    }

    private String userToken() {
        return jwtTokenProvider.generateAccessToken(1L, UserRole.USER);
    }

    private String adminToken() {
        return jwtTokenProvider.generateAccessToken(2L, UserRole.ADMIN);
    }

    private PriceReport saveReport(int price, ReportStatus status, boolean flagged, FlagReason flagReason) {
        return priceReportRepository.save(PriceReport.builder()
            .pharmacy(pharmacy).drug(drug)
            .price(price).purchasedAt(LocalDate.now())
            .status(status).flagged(flagged).flagReason(flagReason)
            .build());
    }

    @Test
    void USER_토큰으로_GET과PATCH_모두_403() throws Exception {
        String token = userToken();

        mockMvc.perform(get("/api/v1/admin/price-reports").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/admin/price-reports/1")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"HIDDEN\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void ADMIN_토큰으로_flagged필터_조회시_이상치제보만반환() throws Exception {
        // seed 데이터(T-08)에도 flagged=true 행이 다수 존재하므로, 새로 만든 pharmacy.id로
        // 함께 필터링해 시드와 섞이지 않게 한다(seed는 이 약국을 참조하지 않는다).
        PriceReport flaggedReport = saveReport(50_000, ReportStatus.ACTIVE, true, FlagReason.OUTLIER_HIGH);
        saveReport(2800, ReportStatus.ACTIVE, false, null);

        mockMvc.perform(get("/api/v1/admin/price-reports")
                .param("flagged", "true")
                .param("pharmacyId", String.valueOf(pharmacy.getId()))
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(flaggedReport.getId()))
            .andExpect(jsonPath("$.content[0].flagReason").value("OUTLIER_HIGH"))
            .andExpect(jsonPath("$.content[0].reporter").doesNotExist());
    }

    @Test
    void ADMIN_토큰으로_status필터로_HIDDEN제보도_조회된다() throws Exception {
        PriceReport hidden = saveReport(2800, ReportStatus.HIDDEN, false, null);
        saveReport(3000, ReportStatus.ACTIVE, false, null);

        mockMvc.perform(get("/api/v1/admin/price-reports")
                .param("status", "HIDDEN")
                .param("pharmacyId", String.valueOf(pharmacy.getId()))
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(hidden.getId()))
            .andExpect(jsonPath("$.content[0].status").value("HIDDEN"));
    }

    /**
     * 2000원·4000원 두 건을 ACTIVE로 심고 2000원 건을 HIDDEN 처리한다.
     * {@code AdminReportServiceImpl}이 {@code saveAndFlush} 없이 recalculate를
     * 호출했다면 native 집계 쿼리가 여전히 두 건 다 ACTIVE로 읽어
     * repPrice가 3000(두 값의 중앙 보간)으로 나왔을 것이다 - 4000이 나와야
     * flush가 실제로 적용됐다는 뜻이다.
     */
    @Test
    void HIDDEN으로_전환하면_rep_price가_즉시재계산된다() throws Exception {
        PriceReport toHide = saveReport(2000, ReportStatus.ACTIVE, false, null);
        saveReport(4000, ReportStatus.ACTIVE, false, null);

        mockMvc.perform(patch("/api/v1/admin/price-reports/" + toHide.getId())
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"HIDDEN\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("HIDDEN"))
            .andExpect(jsonPath("$.recalculatedStat.pharmacyId").value(pharmacy.getId()))
            .andExpect(jsonPath("$.recalculatedStat.drugId").value(drug.getId()))
            .andExpect(jsonPath("$.recalculatedStat.repPrice").value(4000))
            .andExpect(jsonPath("$.recalculatedStat.reportCount").value(1));
    }

    /** 이상치(9000)를 flagged=false로 풀면 정상가(3000)와 함께 재계산에 포함돼야 한다. */
    @Test
    void flagged를_false로_풀면_통계에_재포함된다() throws Exception {
        PriceReport outlier = saveReport(9000, ReportStatus.ACTIVE, true, FlagReason.OUTLIER_HIGH);
        saveReport(3000, ReportStatus.ACTIVE, false, null);

        mockMvc.perform(patch("/api/v1/admin/price-reports/" + outlier.getId())
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flagged\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flagged").value(false))
            .andExpect(jsonPath("$.recalculatedStat.reportCount").value(2))
            .andExpect(jsonPath("$.recalculatedStat.repPrice").value(6000));
    }

    @Test
    void REJECTED_전환시_report_count가_1차감되고_중복차감되지않는다() throws Exception {
        AppUser user = TestFixtures.appUser("admin-report-" + UUID.randomUUID() + "@example.com");
        user.increaseReportCount();
        user = appUserRepository.save(user);

        PriceReport report = priceReportRepository.save(PriceReport.builder()
            .pharmacy(pharmacy).drug(drug).user(user)
            .price(2500).purchasedAt(LocalDate.now())
            .status(ReportStatus.ACTIVE).build());

        mockMvc.perform(patch("/api/v1/admin/price-reports/" + report.getId())
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REJECTED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));

        AppUser afterFirst = appUserRepository.findById(user.getId()).orElseThrow();
        assertThat(afterFirst.getReportCount()).isEqualTo(0);

        // 이미 REJECTED인 제보를 다시 REJECTED로 PATCH해도 추가 차감이 없어야 한다.
        mockMvc.perform(patch("/api/v1/admin/price-reports/" + report.getId())
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REJECTED\"}"))
            .andExpect(status().isOk());

        AppUser afterSecond = appUserRepository.findById(user.getId()).orElseThrow();
        assertThat(afterSecond.getReportCount()).isEqualTo(0);
    }

    @Test
    void 존재하지않는reportId_PATCH시_404_REPORT_NOT_FOUND() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/price-reports/999999999")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"HIDDEN\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }
}
