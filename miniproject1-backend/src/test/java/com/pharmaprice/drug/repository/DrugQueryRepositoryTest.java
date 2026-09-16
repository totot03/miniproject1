package com.pharmaprice.drug.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugQueryRepository.DrugDetailRow;
import com.pharmaprice.drug.repository.DrugQueryRepository.DrugSearchRow;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PriceStatRepository;
import com.pharmaprice.support.TestFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code docs/ROADMAP.md} T-13 / {@code docs/API.md} §3 검증. otc_flag 배제,
 * q(name/display_name) 부분일치, category 필터, 페이지네이션, 조인 집계값을
 * 실제 DB로 확인한다({@code PriceStatServiceTest}와 동일한
 * {@link AbstractIntegrationTest} 스타일).
 *
 * <p>V2 시드 데이터(36건)는 전부 {@code otc_flag=true}라 전문의약품 표본이
 * DB에 없다 — 필터가 실제로 동작하는지 확인하려면 이 테스트가 직접
 * {@code otcFlag=false} 인 Drug을 만들어야 한다.</p>
 */
class DrugQueryRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private DrugRepository drugRepository;

    @Autowired
    private PriceStatRepository priceStatRepository;

    @Autowired
    private DrugQueryRepository drugQueryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Region region;

    @BeforeEach
    void setUpFixtures() {
        region = regionRepository.save(TestFixtures.region());
    }

    private Drug saveDrug(String displayName, String name, String category, boolean otcFlag) {
        Drug drug = Drug.builder()
            .displayName(displayName)
            .name(name)
            .category(category)
            .packageUnit("1개")
            .otcFlag(otcFlag)
            .build();
        Drug saved = drugRepository.save(drug);
        entityManager.flush(); // native 쿼리가 같은 트랜잭션 안에서 즉시 보게 하려면 flush 필요(AbstractIntegrationTest 주석 참고)
        return saved;
    }

    private void saveStat(Pharmacy pharmacy, Drug drug, int repPrice, int reportCount) {
        priceStatRepository.save(PharmacyDrugPriceStat.builder()
            .pharmacy(pharmacy)
            .drug(drug)
            .repPrice(repPrice)
            .minPrice(repPrice)
            .maxPrice(repPrice)
            .avgPrice(repPrice)
            .reportCount(reportCount)
            .lastReportedAt(LocalDate.now())
            .windowDays((short) 90)
            .calculatedAt(OffsetDateTime.now())
            .build());
        entityManager.flush();
    }

    @Test
    void searchExcludesPrescriptionOnlyDrugs() {
        Drug otc = saveDrug("일반약품", "일반약품정", "해열진통", true);
        Drug prescriptionOnly = saveDrug("전문약품", "전문약품정", "해열진통", false);

        List<DrugSearchRow> result = drugQueryRepository.search(null, null, 50, 0);

        assertThat(result).extracting(DrugSearchRow::getId).contains(otc.getId());
        assertThat(result).extracting(DrugSearchRow::getId).doesNotContain(prescriptionOnly.getId());
    }

    @Test
    void searchMatchesQueryAgainstBothDisplayNameAndName() {
        Drug matchedByDisplayName = saveDrug("졸린멀미약", "에이치자로시작하는이름정", "기타", true);
        Drug matchedByName = saveDrug("비슷비슷한이름", "히든네임캡슐", "기타", true);

        List<DrugSearchRow> byDisplayName = drugQueryRepository.search("졸린", null, 50, 0);
        List<DrugSearchRow> byName = drugQueryRepository.search("히든", null, 50, 0);

        assertThat(byDisplayName).extracting(DrugSearchRow::getId).containsExactly(matchedByDisplayName.getId());
        assertThat(byName).extracting(DrugSearchRow::getId).containsExactly(matchedByName.getId());
    }

    @Test
    void searchFiltersByCategory() {
        Drug painkiller = saveDrug("해열진통약품", "해열진통약품정", "해열진통", true);
        Drug digestive = saveDrug("소화제약품", "소화제약품정", "소화제", true);

        List<DrugSearchRow> result = drugQueryRepository.search(null, "소화제", 50, 0);

        assertThat(result).extracting(DrugSearchRow::getId)
            .contains(digestive.getId())
            .doesNotContain(painkiller.getId());
    }

    @Test
    void searchPaginatesWithLimitAndOffset() {
        // V2 시드(36건)가 전부 otc_flag=true라 필터 없이 페이지를 나누면 시드 데이터가
        // 섞여 순서를 예측할 수 없다 — 시드에 없는 전용 category로 범위를 좁힌다.
        String isolatedCategory = "페이지네이션테스트";
        Drug a = saveDrug("가나다약품", "가나다약품정", isolatedCategory, true);
        Drug b = saveDrug("나다라약품", "나다라약품정", isolatedCategory, true);
        Drug c = saveDrug("다라마약품", "다라마약품정", isolatedCategory, true);

        // ORDER BY display_name 이라 가나다 -> 나다라 -> 다라마 순으로 정렬된다.
        List<DrugSearchRow> firstPage = drugQueryRepository.search(null, isolatedCategory, 2, 0);
        List<DrugSearchRow> secondPage = drugQueryRepository.search(null, isolatedCategory, 2, 2);

        assertThat(firstPage).extracting(DrugSearchRow::getId).containsExactly(a.getId(), b.getId());
        assertThat(secondPage).extracting(DrugSearchRow::getId).containsExactly(c.getId());
    }

    @Test
    void searchAggregatesNationalAvgPriceAndPharmacyCountAcrossPharmacies() {
        Drug drug = saveDrug("집계약품", "집계약품정", "기타", true);
        Pharmacy pharmacyA = pharmacyRepository.save(TestFixtures.pharmacy(region));
        Pharmacy pharmacyB = pharmacyRepository.save(TestFixtures.pharmacy(region));
        saveStat(pharmacyA, drug, 1000, 3);
        saveStat(pharmacyB, drug, 2000, 5);

        DrugSearchRow row = drugQueryRepository.search(null, null, 50, 0).stream()
            .filter(r -> r.getId().equals(drug.getId())).findFirst().orElseThrow();

        assertThat(row.getNationalAvgPrice()).isEqualTo(1500); // (1000+2000)/2
        assertThat(row.getPharmacyCount()).isEqualTo(2);
    }

    @Test
    void findDetailAggregatesMinMaxAvgAndSumsReportCount() {
        Drug drug = saveDrug("상세약품", "상세약품정", "기타", true);
        Pharmacy pharmacyA = pharmacyRepository.save(TestFixtures.pharmacy(region));
        Pharmacy pharmacyB = pharmacyRepository.save(TestFixtures.pharmacy(region));
        saveStat(pharmacyA, drug, 1000, 3);
        saveStat(pharmacyB, drug, 2000, 5);

        DrugDetailRow detail = drugQueryRepository.findDetail(drug.getId()).orElseThrow();

        assertThat(detail.getNationalAvg()).isEqualTo(1500);
        assertThat(detail.getNationalMin()).isEqualTo(1000);
        assertThat(detail.getNationalMax()).isEqualTo(2000);
        assertThat(detail.getPharmacyCount()).isEqualTo(2);
        assertThat(detail.getReportCount()).isEqualTo(8); // 3 + 5
    }

    @Test
    void findDetailReturnsEmptyForPrescriptionOnlyDrug() {
        Drug prescriptionOnly = saveDrug("전문의약품상세", "전문의약품상세정", "기타", false);

        Optional<DrugDetailRow> result = drugQueryRepository.findDetail(prescriptionOnly.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void searchRespondsWithinAReasonableTimeAtCurrentDataScale() {
        // 엄격한 200ms SLA 검증이 아니라, 현재 시드 규모(수십 건)에서 쿼리가 비정상적으로
        // 느려지지 않았는지 확인하는 참고용 소프트 어서션이다. 실제 운영 규모 성능은
        // 별도로 측정해야 한다.
        long start = System.nanoTime();
        drugQueryRepository.search(null, null, 8, 0);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(1000);
    }
}
