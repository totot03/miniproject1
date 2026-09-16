package com.pharmaprice.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PriceStatRepository;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.repository.PriceReportRepository;
import com.pharmaprice.support.TestFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code docs/PRD.md} §F3.1 절차(90일 창 → 0건이면 180일 확대 → 4건 이상이면 IQR 이상치
 * 제거 → 중앙값)를 검증한다. native SQL을 쓰는 재계산이라 순수 단위 테스트로는 확인할
 * 수 없어 {@link AbstractIntegrationTest} 기반 통합 테스트로 작성한다.
 */
class PriceStatServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private DrugRepository drugRepository;

    @Autowired
    private PriceReportRepository priceReportRepository;

    @Autowired
    private PriceStatRepository priceStatRepository;

    @Autowired
    private PriceStatService priceStatService;

    @PersistenceContext
    private EntityManager entityManager;

    private Pharmacy pharmacy;
    private Drug drug;

    @BeforeEach
    void setUpFixtures() {
        Region region = regionRepository.save(TestFixtures.region());
        pharmacy = pharmacyRepository.save(TestFixtures.pharmacy(region));
        drug = drugRepository.save(TestFixtures.drug());
    }

    /**
     * 제보를 저장하고 즉시 flush한다. 재계산은 native SQL로 DB를 직접 읽으므로,
     * flush하지 않으면(영속성 컨텍스트 안에만 있으면) 방금 저장한 제보가 보이지 않는다.
     *
     * @return 저장된 제보의 id — V2/V3 시드가 이미 수만 건의 price_report를 깔아 둔
     *     상태라, 나중에 다시 찾을 때 {@code findAll()} 같은 걸로 뒤지면 시드 데이터를
     *     집을 위험이 있다. id를 직접 들고 있다가 {@code findById}로 되찾는다.
     */
    private Long savePriceReport(int price, LocalDate purchasedAt) {
        Long id = priceReportRepository.save(
            PriceReport.builder()
                .pharmacy(pharmacy)
                .drug(drug)
                .price(price)
                .purchasedAt(purchasedAt)
                .build()).getId();
        entityManager.flush();
        return id;
    }

    @Test
    void medianIsNotSkewedByASingleOutlierAmongFiveReports() {
        savePriceReport(1000, LocalDate.now());
        savePriceReport(1000, LocalDate.now());
        savePriceReport(1000, LocalDate.now());
        savePriceReport(1000, LocalDate.now());
        savePriceReport(50_000, LocalDate.now()); // 극단값 — IQR로 제거되어야 한다

        PharmacyDrugPriceStat stat = priceStatService.recalculate(pharmacy.getId(), drug.getId()).orElseThrow();

        assertThat(stat.getRepPrice()).isEqualTo(1000);
        assertThat(stat.getReportCount()).isEqualTo(4); // 극단값 1건이 제거됨
        assertThat(stat.getWindowDays()).isEqualTo((short) 90);
    }

    @Test
    void medianIsUsedWithoutIqrFilteringWhenFewerThanFourReports() {
        savePriceReport(1000, LocalDate.now());
        savePriceReport(2000, LocalDate.now());
        savePriceReport(90_000, LocalDate.now()); // 3건뿐이라 이상치 제거 없이 그대로 반영

        PharmacyDrugPriceStat stat = priceStatService.recalculate(pharmacy.getId(), drug.getId()).orElseThrow();

        assertThat(stat.getRepPrice()).isEqualTo(2000);
        assertThat(stat.getReportCount()).isEqualTo(3);
    }

    @Test
    void expandsTo180DayWindowWhenNoReportsWithin90Days() {
        savePriceReport(3000, LocalDate.now().minusDays(120)); // 90일 밖, 180일 안

        PharmacyDrugPriceStat stat = priceStatService.recalculate(pharmacy.getId(), drug.getId()).orElseThrow();

        assertThat(stat.getWindowDays()).isEqualTo((short) 180);
        assertThat(stat.getRepPrice()).isEqualTo(3000);
    }

    @Test
    void deletesStatRowWhenNoValidReportsRemain() {
        Long reportId = savePriceReport(3000, LocalDate.now());
        priceStatService.recalculate(pharmacy.getId(), drug.getId()).orElseThrow();

        // recalculate()의 native upsert가 영속성 컨텍스트를 비우므로(clearAutomatically),
        // 앞서 저장했던 report 참조는 detached 상태다 — id로 다시 조회해 관리 상태로 되돌린다.
        PriceReport report = priceReportRepository.findById(reportId).orElseThrow();
        report.hide();
        entityManager.flush();

        Optional<PharmacyDrugPriceStat> result = priceStatService.recalculate(pharmacy.getId(), drug.getId());

        assertThat(result).isEmpty();
        assertThat(priceStatRepository.findByPharmacyIdAndDrugId(pharmacy.getId(), drug.getId())).isEmpty();
    }

    @Test
    void recalculatingTwiceProducesTheSameResult() {
        savePriceReport(1000, LocalDate.now());
        savePriceReport(1200, LocalDate.now());
        savePriceReport(1100, LocalDate.now());

        PharmacyDrugPriceStat first = priceStatService.recalculate(pharmacy.getId(), drug.getId()).orElseThrow();
        PharmacyDrugPriceStat second = priceStatService.recalculate(pharmacy.getId(), drug.getId()).orElseThrow();

        assertThat(second.getRepPrice()).isEqualTo(first.getRepPrice());
        assertThat(second.getMinPrice()).isEqualTo(first.getMinPrice());
        assertThat(second.getMaxPrice()).isEqualTo(first.getMaxPrice());
        assertThat(second.getAvgPrice()).isEqualTo(first.getAvgPrice());
        assertThat(second.getReportCount()).isEqualTo(first.getReportCount());
        assertThat(second.getWindowDays()).isEqualTo(first.getWindowDays());
        // calculatedAt은 호출마다 달라질 수 있어 비교하지 않는다.
    }
}
