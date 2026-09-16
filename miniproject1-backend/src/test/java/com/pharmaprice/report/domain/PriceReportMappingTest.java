package com.pharmaprice.report.domain;

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
import com.pharmaprice.report.repository.PriceReportRepository;
import com.pharmaprice.report.repository.UploadedFileRepository;
import com.pharmaprice.support.TestFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * price_report 저장·조회, enum 이 DB 에 문자열로 저장되는지(native query),
 * uploaded_file / pharmacy_drug_price_stat 매핑, 감사 컬럼(created_at,
 * updated_at) 자동 채움을 검증한다.
 */
class PriceReportMappingTest extends AbstractIntegrationTest {

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private DrugRepository drugRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private PriceReportRepository priceReportRepository;

    @Autowired
    private PriceStatRepository priceStatRepository;

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

    @Test
    void priceReportIsSavedWithDefaultEnumsAndAuditColumns() {
        PriceReport report = PriceReport.builder()
            .pharmacy(pharmacy)
            .drug(drug)
            .price(2800)
            .purchasedAt(LocalDate.now())
            .build();

        Long id = priceReportRepository.save(report).getId();
        entityManager.flush();
        entityManager.clear();

        PriceReport found = priceReportRepository.findById(id).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ReportStatus.ACTIVE);
        assertThat(found.getSource()).isEqualTo(ReportSource.FORM);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();

        // enum 이 DB 에 문자열로 저장되는지 native query 로 직접 확인한다.
        // JPQL 로 읽으면 다시 enum 으로 역직렬화돼 검증 의미가 없어진다.
        String rawStatus = (String) entityManager
            .createNativeQuery("SELECT status FROM price_report WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
        assertThat(rawStatus).isEqualTo("ACTIVE");
    }

    @Test
    void updatedAtAdvancesOnModification() throws InterruptedException {
        PriceReport report = priceReportRepository.save(
            PriceReport.builder()
                .pharmacy(pharmacy)
                .drug(drug)
                .price(2800)
                .purchasedAt(LocalDate.now())
                .build());
        entityManager.flush();
        OffsetDateTime firstUpdatedAt = report.getUpdatedAt();

        Thread.sleep(10);
        report.hide();
        entityManager.flush();
        entityManager.clear();

        PriceReport found = priceReportRepository.findById(report.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ReportStatus.HIDDEN);
        assertThat(found.getUpdatedAt()).isAfter(firstUpdatedAt);
    }

    @Test
    void uploadedFileIsSavedAndReferencedByPriceReport() {
        UploadedFile file = uploadedFileRepository.save(
            UploadedFile.builder()
                .originalName("receipt.jpg")
                .storedPath("/app/uploads/2026/09/uuid.jpg")
                .contentType("image/jpeg")
                .sizeBytes(102_400L)
                .build());

        PriceReport report = priceReportRepository.save(
            PriceReport.builder()
                .pharmacy(pharmacy)
                .drug(drug)
                .price(2800)
                .purchasedAt(LocalDate.now())
                .receiptFile(file)
                .build());

        entityManager.flush();
        entityManager.clear();

        PriceReport found = priceReportRepository.findById(report.getId()).orElseThrow();
        assertThat(found.getReceiptFile().getId()).isEqualTo(file.getId());
        assertThat(found.getReceiptFile().getCreatedAt()).isNotNull();
    }

    @Test
    void pharmacyDrugPriceStatIsSavedWithShortWindowDays() {
        PharmacyDrugPriceStat stat = PharmacyDrugPriceStat.builder()
            .pharmacy(pharmacy)
            .drug(drug)
            .repPrice(2800)
            .minPrice(2700)
            .maxPrice(3000)
            .avgPrice(2830)
            .reportCount(5)
            .lastReportedAt(LocalDate.now())
            .windowDays((short) 90)
            .calculatedAt(OffsetDateTime.now())
            .build();

        Long id = priceStatRepository.save(stat).getId();
        entityManager.flush();
        entityManager.clear();

        PharmacyDrugPriceStat found = priceStatRepository.findById(id).orElseThrow();
        assertThat(found.getWindowDays()).isEqualTo((short) 90);
        assertThat(found.getRepPrice()).isEqualTo(2800);
    }
}
