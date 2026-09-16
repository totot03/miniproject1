package com.pharmaprice.pharmacy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.support.TestFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Region 의 자연키(String PK) 저장·조회와, Pharmacy 의
 * business_hours(JSONB) ↔ Map 왕복을 검증한다.
 */
class PharmacyMappingTest extends AbstractIntegrationTest {

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void regionIsStoredAndFetchedByNaturalKey() {
        regionRepository.save(TestFixtures.region());
        entityManager.flush();
        entityManager.clear();

        Region found = regionRepository.findById("11680").orElseThrow();

        assertThat(found.getSido()).isEqualTo("서울특별시");
        assertThat(found.getSigungu()).isEqualTo("강남구");
        assertThat(found.getCenterLat()).isEqualTo(37.4979);
    }

    @Test
    void businessHoursRoundTripsThroughJsonb() {
        Region region = regionRepository.save(TestFixtures.region());

        Map<String, List<String>> businessHours = new LinkedHashMap<>();
        businessHours.put("mon", List.of("09:00", "19:00"));
        businessHours.put("sun", null);
        businessHours.put("holiday", null);

        Pharmacy pharmacy = Pharmacy.builder()
            .name("가온약국")
            .region(region)
            .lat(37.5012)
            .lng(127.0396)
            .businessHours(businessHours)
            .build();
        Long savedId = pharmacyRepository.save(pharmacy).getId();

        entityManager.flush();
        entityManager.clear();

        Pharmacy found = pharmacyRepository.findById(savedId).orElseThrow();

        assertThat(found.getBusinessHours())
            .containsEntry("mon", List.of("09:00", "19:00"))
            .containsEntry("sun", null)
            .containsEntry("holiday", null);
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
