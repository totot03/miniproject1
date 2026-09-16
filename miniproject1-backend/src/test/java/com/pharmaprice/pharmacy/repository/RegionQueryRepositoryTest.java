package com.pharmaprice.pharmacy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.RegionQueryRepository.RegionRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code docs/ROADMAP.md} T-14 / {@code docs/API.md} §7 검증. LEFT JOIN으로
 * 약국이 0개인 지역도 남는지, 폐업 약국이 카운트에서 빠지는지를 실제 DB로
 * 확인한다({@code DrugQueryRepositoryTest}와 동일한 {@link AbstractIntegrationTest} 스타일).
 *
 * <p>T-07 시드가 이미 {@code region} ≥20건 / {@code pharmacy} ≥300건을
 * 채워뒀고 {@code TestFixtures.region()}의 코드({@code "11680"} 강남구)에도
 * 시드 약국이 몰려 있어, 공유 픽스처를 쓰면 카운트 예측이 불가능해진다.
 * 그래서 이 테스트는 시드와 절대 겹치지 않을 전용 코드({@code "99999"})로
 * {@code Region}을 직접 만들어 격리한다.</p>
 */
class RegionQueryRepositoryTest extends AbstractIntegrationTest {

    private static final String ISOLATED_CODE = "99999";

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private RegionQueryRepository regionQueryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Region isolatedRegion() {
        Region region = Region.builder()
            .code(ISOLATED_CODE)
            .sido("테스트특별시")
            .sigungu("격리구")
            .centerLat(37.1)
            .centerLng(127.1)
            .build();
        Region saved = regionRepository.save(region);
        entityManager.flush(); // native 쿼리가 같은 트랜잭션 안에서 즉시 보게 하려면 flush 필요
        return saved;
    }

    private Pharmacy savePharmacy(Region region, boolean active) {
        Pharmacy pharmacy = Pharmacy.builder()
            .name("격리약국")
            .region(region)
            .lat(37.1)
            .lng(127.1)
            .active(active)
            .build();
        Pharmacy saved = pharmacyRepository.save(pharmacy);
        entityManager.flush();
        return saved;
    }

    private RegionRow findIsolated() {
        return regionQueryRepository.listWithPharmacyCount().stream()
            .filter(row -> row.getCode().equals(ISOLATED_CODE))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void includesRegionsWithNoPharmacies() {
        isolatedRegion();

        Optional<RegionRow> found = regionQueryRepository.listWithPharmacyCount().stream()
            .filter(row -> row.getCode().equals(ISOLATED_CODE))
            .findFirst();

        assertThat(found).isPresent();
        assertThat(found.get().getPharmacyCount()).isZero();
    }

    @Test
    void countsOnlyActivePharmaciesInRegion() {
        Region region = isolatedRegion();
        savePharmacy(region, true);
        savePharmacy(region, true);
        savePharmacy(region, false); // 폐업 — 카운트에서 제외돼야 한다

        RegionRow row = findIsolated();

        assertThat(row.getPharmacyCount()).isEqualTo(2);
    }

    @Test
    void mapsCenterCoordinatesAndSigunguFields() {
        isolatedRegion();

        RegionRow row = findIsolated();

        assertThat(row.getSido()).isEqualTo("테스트특별시");
        assertThat(row.getSigungu()).isEqualTo("격리구");
        assertThat(row.getCenterLat()).isEqualTo(37.1);
        assertThat(row.getCenterLng()).isEqualTo(127.1);
    }

    @Test
    void resultIsSortedBySidoThenSigungu() {
        isolatedRegion();

        List<RegionRow> rows = regionQueryRepository.listWithPharmacyCount();

        List<String> sortedKeys = rows.stream().map(r -> r.getSido() + r.getSigungu()).sorted().toList();
        List<String> actualKeys = rows.stream().map(r -> r.getSido() + r.getSigungu()).toList();
        assertThat(actualKeys).isEqualTo(sortedKeys);
    }
}
