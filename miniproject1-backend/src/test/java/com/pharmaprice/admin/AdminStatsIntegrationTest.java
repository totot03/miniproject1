package com.pharmaprice.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.auth.security.JwtTokenProvider;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PriceStatRepository;
import com.pharmaprice.support.TestFixtures;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code docs/ROADMAP.md} T-31 완료 판정을 실제 {@code pharmaprice_test} DB(V1~V3
 * 시드 전부 적용됨 - dev DB와 동일 데이터)로 검증한다. {@link com.pharmaprice.admin.service.AdminStatsServiceImplTest}
 * (Mockito)로는 native 쿼리(추이·지역별·히스토그램·전국통계·가격격차)의 실제 정확성을
 * 확인할 수 없어 이 통합 테스트가 유일한 검증 지점이다.
 *
 * <p>{@code price-gaps}는 seed 약국 400건이 전부 지역 1곳(강남구)에 몰려 있어
 * (2026-09-18 실측, {@code seed-data-single-region-pharmacy} 메모 참고) 기존 시드
 * 데이터만으로는 "지역 3곳 이상" 조건을 만족하는 약품이 하나도 없다. 그래서
 * {@code gapPct} 검증은 시드에 기대지 않고, 이 테스트가 직접 3개 지역 × 3개 약국
 * 통계를 심어 통제된 시나리오로 수기 검산한다 - {@code Pharmacy}/{@code PharmacyDrugPriceStat}
 * 모두 {@code IDENTITY} 채번이라 {@code save()} 시점에 즉시 INSERT되므로, 같은 트랜잭션
 * 안의 이어지는 native 쿼리에서 별도 {@code flush()} 없이도 보인다.</p>
 */
@AutoConfigureMockMvc
class AdminStatsIntegrationTest extends AbstractIntegrationTest {

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
    private PriceStatRepository priceStatRepository;

    private String userToken() {
        return jwtTokenProvider.generateAccessToken(1L, UserRole.USER);
    }

    private String adminToken() {
        return jwtTokenProvider.generateAccessToken(2L, UserRole.ADMIN);
    }

    @Test
    void USER_토큰으로_4개엔드포인트모두_403() throws Exception {
        String token = userToken();

        for (String path : List.of("/api/v1/admin/stats/overview", "/api/v1/admin/stats/regions",
            "/api/v1/admin/stats/drugs/1", "/api/v1/admin/stats/price-gaps")) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void ADMIN_토큰으로_overview는_200과_totals_필드를반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/overview").header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totals.pharmacyCount").isNumber())
            .andExpect(jsonPath("$.totals.drugCount").isNumber())
            .andExpect(jsonPath("$.totals.coveredPairCount").isNumber())
            .andExpect(jsonPath("$.flaggedReportCount").isNumber())
            .andExpect(jsonPath("$.coverageRate").isNumber());
    }

    @Test
    void ADMIN_토큰으로_regions는_200을반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/regions").header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows").isArray());
    }

    @Test
    void ADMIN_토큰으로_drugStats는_200과_약품정보를반환한다() throws Exception {
        Drug drug = drugRepository.save(TestFixtures.drug());

        mockMvc.perform(get("/api/v1/admin/stats/drugs/" + drug.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.drug.id").value(drug.getId()))
            .andExpect(jsonPath("$.drug.displayName").value(drug.getDisplayName()))
            .andExpect(jsonPath("$.distribution").isArray())
            .andExpect(jsonPath("$.national").exists());
    }

    @Test
    void ADMIN_토큰으로_존재하지않는drugId조회시_404_DRUG_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/drugs/999999999")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DRUG_NOT_FOUND"));
    }

    /**
     * 지역 3곳(각 약국 3곳, rep_price 평균 2000/3000/4000)을 직접 심어 최저가·최고가
     * 지역이 정확히 뽑히고 gap·gapPct가 수기 계산과 일치하는지 검증한다.
     */
    @Test
    void priceGaps의_gap과_gapPct는_수기계산과일치한다() throws Exception {
        Drug drug = drugRepository.save(TestFixtures.drug());
        Region cheapest = saveRegion("T31RGA", "테스트시도", "저가구");
        Region middle = saveRegion("T31RGB", "테스트시도", "중간구");
        Region priciest = saveRegion("T31RGC", "테스트시도", "고가구");

        saveStatsForRegion(drug, cheapest, List.of(1900, 2000, 2100));   // avg 2000
        saveStatsForRegion(drug, middle, List.of(2900, 3000, 3100));    // avg 3000 (지역 수 조건 충족용)
        saveStatsForRegion(drug, priciest, List.of(3900, 4000, 4100));  // avg 4000

        mockMvc.perform(get("/api/v1/admin/stats/price-gaps")
                .param("limit", "50")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].drug.id").value(drug.getId()))
            .andExpect(jsonPath("$.rows[0].cheapestRegion.sigungu").value("저가구"))
            .andExpect(jsonPath("$.rows[0].cheapestRegion.avgPrice").value(2000))
            .andExpect(jsonPath("$.rows[0].priciestRegion.sigungu").value("고가구"))
            .andExpect(jsonPath("$.rows[0].priciestRegion.avgPrice").value(4000))
            .andExpect(jsonPath("$.rows[0].gap").value(2000))
            .andExpect(jsonPath("$.rows[0].gapPct").value(100.0));
    }

    private Region saveRegion(String code, String sido, String sigungu) {
        return regionRepository.save(Region.builder()
            .code(code).sido(sido).sigungu(sigungu).centerLat(37.0).centerLng(127.0).build());
    }

    private void saveStatsForRegion(Drug drug, Region region, List<Integer> prices) {
        for (int price : prices) {
            Pharmacy pharmacy = pharmacyRepository.save(Pharmacy.builder()
                .name("약국-" + region.getCode() + "-" + price)
                .region(region).lat(37.0).lng(127.0).build());
            priceStatRepository.save(PharmacyDrugPriceStat.builder()
                .pharmacy(pharmacy).drug(drug)
                .repPrice(price).minPrice(price).maxPrice(price).avgPrice(price)
                .reportCount(1).lastReportedAt(LocalDate.now()).windowDays((short) 90)
                .calculatedAt(OffsetDateTime.now())
                .build());
        }
    }
}
