package com.pharmaprice.pharmacy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.PharmacyQueryRepository.DrugPriceRow;
import com.pharmaprice.pharmacy.repository.PharmacyQueryRepository.PharmacyRow;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PriceStatRepository;
import com.pharmaprice.support.TestFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code docs/ROADMAP.md} T-19 / {@code docs/API.md} §4 검증. q(이름·주소)
 * 부분일치, is_active 필터, 바운딩박스 근접 검색, drugPrices 정렬·전국평균
 * 집계를 실제 DB로 확인한다({@code DrugQueryRepositoryTest}와 동일한
 * {@link AbstractIntegrationTest} 스타일).
 */
class PharmacyQueryRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private DrugRepository drugRepository;

    @Autowired
    private PriceStatRepository priceStatRepository;

    @Autowired
    private PharmacyQueryRepository pharmacyQueryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Region region;

    @BeforeEach
    void setUpFixtures() {
        region = regionRepository.save(TestFixtures.region());
    }

    private Pharmacy savePharmacy(String name, String addressRoad, double lat, double lng, boolean active) {
        Pharmacy pharmacy = Pharmacy.builder()
            .name(name)
            .addressRoad(addressRoad)
            .region(region)
            .lat(lat)
            .lng(lng)
            .active(active)
            .build();
        Pharmacy saved = pharmacyRepository.save(pharmacy);
        entityManager.flush(); // native 쿼리가 같은 트랜잭션 안에서 즉시 보게 하려면 flush 필요
        return saved;
    }

    private void saveStat(Pharmacy pharmacy, Drug drug, int repPrice, int minPrice, int maxPrice) {
        priceStatRepository.save(PharmacyDrugPriceStat.builder()
            .pharmacy(pharmacy)
            .drug(drug)
            .repPrice(repPrice)
            .minPrice(minPrice)
            .maxPrice(maxPrice)
            .avgPrice(repPrice)
            .reportCount(3)
            .lastReportedAt(LocalDate.now())
            .windowDays((short) 90)
            .calculatedAt(OffsetDateTime.now())
            .build());
        entityManager.flush();
    }

    /**
     * pharmaprice_test DB는 비어있지 않다 — AbstractIntegrationTest가 상속하는
     * Flyway 마이그레이션에는 V2/V3 시드(실제 약국·가격 데이터, docs/ROADMAP.md T-07)가
     * 포함돼 있다({@code DrugQueryRepositoryTest}가 "V2 시드가 섞인다"고 이미 남긴
     * 주석과 동일한 함정). 그래서 흔한 이름이나 {@code q=null}로 조회하면 시드
     * 데이터가 섞여 결과를 예측할 수 없다 — 시드에 있을 리 없는 고유 토큰을
     * 이름·주소에 심어 그 토큰으로만 조회한다.
     */
    private static final String ISOLATED_TOKEN = "약국픽스처테스트전용토큰";

    @Test
    void searchByQueryMatchesNameOrAddressRoad() {
        Pharmacy byName = savePharmacy(ISOLATED_TOKEN + "이름약국", "무관한주소", 37.50, 127.00, true);
        Pharmacy byAddress = savePharmacy("이름은달라요약국", ISOLATED_TOKEN + "로123", 37.50, 127.00, true);
        savePharmacy("무관한약국", "무관한주소", 37.50, 127.00, true);

        List<PharmacyRow> result = pharmacyQueryRepository.searchByQuery(ISOLATED_TOKEN, 20, 0);

        assertThat(result).extracting(PharmacyRow::getId)
            .containsExactlyInAnyOrder(byName.getId(), byAddress.getId());
    }

    @Test
    void searchByQueryExcludesInactivePharmacies() {
        Pharmacy active = savePharmacy(ISOLATED_TOKEN + "활성약국", ISOLATED_TOKEN, 37.50, 127.00, true);
        savePharmacy(ISOLATED_TOKEN + "폐업약국", ISOLATED_TOKEN, 37.50, 127.00, false);

        List<PharmacyRow> result = pharmacyQueryRepository.searchByQuery(ISOLATED_TOKEN, 20, 0);

        assertThat(result).extracting(PharmacyRow::getId).containsExactly(active.getId());
    }

    @Test
    void searchByQueryPaginatesAndCountMatchesTotal() {
        String isolatedAddress = "페이지네이션테스트로";
        Pharmacy a = savePharmacy("가나다약국", isolatedAddress, 37.50, 127.00, true);
        Pharmacy b = savePharmacy("나다라약국", isolatedAddress, 37.50, 127.00, true);
        Pharmacy c = savePharmacy("다라마약국", isolatedAddress, 37.50, 127.00, true);

        // ORDER BY name이라 가나다 -> 나다라 -> 다라마 순으로 정렬된다.
        List<PharmacyRow> firstPage = pharmacyQueryRepository.searchByQuery(isolatedAddress, 2, 0);
        List<PharmacyRow> secondPage = pharmacyQueryRepository.searchByQuery(isolatedAddress, 2, 2);
        long total = pharmacyQueryRepository.countByQuery(isolatedAddress);

        assertThat(firstPage).extracting(PharmacyRow::getId).containsExactly(a.getId(), b.getId());
        assertThat(secondPage).extracting(PharmacyRow::getId).containsExactly(c.getId());
        assertThat(total).isEqualTo(3);
    }

    @Test
    void searchByQueryIncludesRegionInfoWhenPresent() {
        Pharmacy pharmacy = savePharmacy(ISOLATED_TOKEN + "지역있는약국", ISOLATED_TOKEN, 37.50, 127.00, true);

        PharmacyRow row = pharmacyQueryRepository.searchByQuery(ISOLATED_TOKEN, 20, 0).stream()
            .filter(r -> r.getId().equals(pharmacy.getId())).findFirst().orElseThrow();

        assertThat(row.getRegionCode()).isEqualTo(region.getCode());
        assertThat(row.getSido()).isEqualTo(region.getSido());
        assertThat(row.getSigungu()).isEqualTo(region.getSigungu());
    }

    @Test
    void findCandidatesNearbyFiltersByBoundingBoxAndOptionalQuery() {
        // 실제 시드 약국도 이 좌표 범위 안에 있을 수 있으므로(강남 일대), q에도
        // ISOLATED_TOKEN을 걸어 시드 데이터와 절대 섞이지 않게 한다.
        Pharmacy inside = savePharmacy(ISOLATED_TOKEN + "근처약국", ISOLATED_TOKEN, 37.501, 127.001, true);
        Pharmacy outside = savePharmacy(ISOLATED_TOKEN + "먼약국", ISOLATED_TOKEN, 38.5, 128.5, true);

        List<PharmacyRow> result = pharmacyQueryRepository.findCandidatesNearby(
            ISOLATED_TOKEN, 37.49, 37.51, 126.99, 127.01);

        assertThat(result).extracting(PharmacyRow::getId).containsExactly(inside.getId());
        assertThat(result).extracting(PharmacyRow::getId).doesNotContain(outside.getId());
    }

    @Test
    void findCandidatesNearbyAppliesQueryFilterWithinBoundingBox() {
        Pharmacy matching = savePharmacy(ISOLATED_TOKEN + "가온약국", ISOLATED_TOKEN, 37.501, 127.001, true);
        savePharmacy("다른이름약국", ISOLATED_TOKEN, 37.501, 127.001, true);

        List<PharmacyRow> result = pharmacyQueryRepository.findCandidatesNearby(
            ISOLATED_TOKEN + "가온", 37.49, 37.51, 126.99, 127.01);

        assertThat(result).extracting(PharmacyRow::getId).containsExactly(matching.getId());
    }

    @Test
    void findDrugPricesOrdersByRepPriceAscendingAndIncludesNationalAvg() {
        Pharmacy pharmacy = savePharmacy("가격순약국", "주소", 37.50, 127.00, true);
        Pharmacy otherPharmacy = savePharmacy("전국평균용약국", "주소", 37.50, 127.00, true);
        Drug cheap = drugRepository.save(TestFixtures.drug());
        entityManager.flush();
        Drug expensive = drugRepository.save(Drug.builder()
            .name("비싼약품정").displayName("비싼약품").category("기타").packageUnit("1개").build());
        entityManager.flush();

        saveStat(pharmacy, cheap, 2000, 1900, 2100);
        saveStat(pharmacy, expensive, 5000, 4900, 5100);
        saveStat(otherPharmacy, cheap, 3000, 2900, 3100); // 전국평균 = (2000+3000)/2 = 2500

        List<DrugPriceRow> result = pharmacyQueryRepository.findDrugPrices(pharmacy.getId());

        assertThat(result).extracting(DrugPriceRow::getDrugId)
            .containsExactly(cheap.getId(), expensive.getId()); // repPrice 오름차순
        DrugPriceRow cheapRow = result.get(0);
        assertThat(cheapRow.getRepPrice()).isEqualTo(2000);
        assertThat(cheapRow.getMaxPrice()).isEqualTo(2100);
        assertThat(cheapRow.getNationalAvg()).isEqualTo(2500);
    }
}
