package com.pharmaprice.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.SearchQueryRepository.CandidateRow;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportSource;
import com.pharmaprice.report.repository.PriceReportRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code docs/ROADMAP.md} T-15 / {@code docs/API.md} §5 검증. 바운딩 박스 필터,
 * {@code is_active}/통계 없는 약국 배제, {@code dataSource} 판정용 distinct-source
 * 쿼리를 실제 DB로 확인한다({@code DrugQueryRepositoryTest}와 동일한
 * {@link AbstractIntegrationTest} 스타일). 정확 거리 2차 필터는 이 리포지토리의
 * 책임이 아니므로(바운딩 박스까지만 SQL이 처리) 여기서는 검증하지 않는다 —
 * {@code SearchServiceImplTest}가 담당한다.
 */
class SearchQueryRepositoryTest extends AbstractIntegrationTest {

    private static final double CENTER_LAT = 37.50;
    private static final double CENTER_LNG = 127.00;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private DrugRepository drugRepository;

    @Autowired
    private PriceStatRepository priceStatRepository;

    @Autowired
    private PriceReportRepository priceReportRepository;

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Region region;

    @BeforeEach
    void setUp() {
        region = regionRepository.save(Region.builder()
            .code("88888").sido("검색테스트시").sigungu("격리구")
            .centerLat(CENTER_LAT).centerLng(CENTER_LNG).build());
        entityManager.flush();
    }

    private Drug saveDrug(String category) {
        Drug drug = drugRepository.save(Drug.builder()
            .name("검색테스트약품").displayName("검색테스트약품")
            .category(category).packageUnit("1개").otcFlag(true).build());
        entityManager.flush();
        return drug;
    }

    private Pharmacy savePharmacy(double lat, double lng, boolean active) {
        Pharmacy pharmacy = pharmacyRepository.save(Pharmacy.builder()
            .name("검색테스트약국").region(region).lat(lat).lng(lng).active(active).build());
        entityManager.flush();
        return pharmacy;
    }

    private void saveStat(Pharmacy pharmacy, Drug drug, int repPrice) {
        priceStatRepository.save(PharmacyDrugPriceStat.builder()
            .pharmacy(pharmacy).drug(drug)
            .repPrice(repPrice).minPrice(repPrice).maxPrice(repPrice).avgPrice(repPrice)
            .reportCount(1).lastReportedAt(LocalDate.now()).windowDays((short) 90)
            .calculatedAt(OffsetDateTime.now()).build());
        entityManager.flush();
    }

    private void saveReport(Pharmacy pharmacy, Drug drug, ReportSource source) {
        priceReportRepository.save(PriceReport.builder()
            .pharmacy(pharmacy).drug(drug).price(3000).purchasedAt(LocalDate.now())
            .source(source).build());
        entityManager.flush();
    }

    @Test
    void findCandidatesInBoundingBoxExcludesInactiveAndStatlessPharmacies() {
        Drug drug = saveDrug("검색카테고리1");
        Pharmacy withStat = savePharmacy(37.505, 127.005, true);
        Pharmacy noStat = savePharmacy(37.503, 127.003, true);
        Pharmacy inactive = savePharmacy(37.502, 127.002, false);
        saveStat(withStat, drug, 3000);
        saveStat(inactive, drug, 3000); // 폐업 약국도 통계는 있을 수 있다 — is_active 조건으로 걸러져야 한다

        List<CandidateRow> result = searchQueryRepository.findCandidatesInBoundingBox(
            drug.getId(), 37.48, 37.52, 126.97, 127.03);

        assertThat(result).extracting(CandidateRow::getPharmacyId).containsExactly(withStat.getId());
    }

    @Test
    void findCandidatesInBoundingBoxExcludesPharmaciesOutsideBox() {
        Drug drug = saveDrug("검색카테고리2");
        Pharmacy inBox = savePharmacy(37.505, 127.005, true);
        Pharmacy outOfBox = savePharmacy(38.5, 127.00, true); // 위도 1도 이상 차이 — 어떤 바운딩 박스에도 안 걸림
        saveStat(inBox, drug, 3000);
        saveStat(outOfBox, drug, 3000);

        List<CandidateRow> result = searchQueryRepository.findCandidatesInBoundingBox(
            drug.getId(), 37.48, 37.52, 126.97, 127.03);

        assertThat(result).extracting(CandidateRow::getPharmacyId).containsExactly(inBox.getId());
    }

    @Test
    void findCandidatesInBoundingBoxMapsAllRowFields() {
        Drug drug = saveDrug("검색카테고리3");
        Pharmacy pharmacy = savePharmacy(37.505, 127.005, true);
        saveStat(pharmacy, drug, 2500);

        List<CandidateRow> result = searchQueryRepository.findCandidatesInBoundingBox(
            drug.getId(), 37.48, 37.52, 126.97, 127.03);

        assertThat(result).hasSize(1);
        CandidateRow row = result.get(0);
        assertThat(row.getName()).isEqualTo("검색테스트약국");
        assertThat(row.getRepPrice()).isEqualTo(2500);
        assertThat(row.getLat()).isEqualTo(37.505);
        assertThat(row.getLng()).isEqualTo(127.005);
    }

    @Test
    void findDistinctSourcesDetectsSeedOnly() {
        Drug drug = saveDrug("검색카테고리4");
        Pharmacy pharmacy = savePharmacy(37.505, 127.005, true);
        saveReport(pharmacy, drug, ReportSource.SEED);

        List<String> sources = searchQueryRepository.findDistinctSources(drug.getId(), List.of(pharmacy.getId()), 180);

        assertThat(sources).containsExactly("SEED");
    }

    @Test
    void findDistinctSourcesDetectsMixedSources() {
        Drug drug = saveDrug("검색카테고리5");
        Pharmacy pharmacyA = savePharmacy(37.505, 127.005, true);
        Pharmacy pharmacyB = savePharmacy(37.504, 127.004, true);
        saveReport(pharmacyA, drug, ReportSource.SEED);
        saveReport(pharmacyB, drug, ReportSource.FORM);

        List<String> sources = searchQueryRepository.findDistinctSources(
            drug.getId(), List.of(pharmacyA.getId(), pharmacyB.getId()), 180);

        assertThat(sources).containsExactlyInAnyOrder("SEED", "FORM");
    }
}
