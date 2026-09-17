package com.pharmaprice.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.service.PriceStatService;
import com.pharmaprice.report.domain.FlagReason;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.UploadedFile;
import com.pharmaprice.report.dto.PriceReportCreateRequest;
import com.pharmaprice.report.dto.PriceReportResponse;
import com.pharmaprice.report.exception.DrugNotFoundException;
import com.pharmaprice.report.exception.DrugNotOtcException;
import com.pharmaprice.report.exception.InvalidDateRangeException;
import com.pharmaprice.report.exception.PharmacyNotFoundException;
import com.pharmaprice.report.repository.PriceReportRepository;
import com.pharmaprice.report.repository.UploadedFileRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@code docs/ROADMAP.md} T-26 {@link PriceReportServiceImpl} 비즈니스 로직 검증.
 * 모든 리포지토리와 {@link PriceStatService}는 Mockito 목이다 - native SQL(중앙값 계산,
 * DB 유니크 인덱스 위반)의 실제 정확성은 {@code PriceReportIntegrationTest}가 담당한다.
 */
class PriceReportServiceImplTest {

    private static final long PHARMACY_ID = 1L;
    private static final long DRUG_ID = 10L;
    private static final long USER_ID = 100L;

    private final PharmacyRepository pharmacyRepository = mock(PharmacyRepository.class);
    private final DrugRepository drugRepository = mock(DrugRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final UploadedFileRepository uploadedFileRepository = mock(UploadedFileRepository.class);
    private final PriceReportRepository priceReportRepository = mock(PriceReportRepository.class);
    private final PriceStatService priceStatService = mock(PriceStatService.class);

    private PriceReportServiceImpl service;
    private Pharmacy pharmacy;
    private Drug otcDrug;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new PriceReportServiceImpl(
            pharmacyRepository, drugRepository, appUserRepository, uploadedFileRepository,
            priceReportRepository, priceStatService);

        pharmacy = Pharmacy.builder().id(PHARMACY_ID).name("가온약국").lat(37.5).lng(127.0).build();
        otcDrug = Drug.builder().id(DRUG_ID).name("타이레놀정500밀리그람").displayName("타이레놀 500mg")
            .category("해열진통").packageUnit("8정").otcFlag(true).build();
        user = AppUser.builder().id(USER_ID).email("user@test.com").passwordHash("HASHED").nickname("닉네임").build();

        given(pharmacyRepository.findById(PHARMACY_ID)).willReturn(Optional.of(pharmacy));
        given(drugRepository.findById(DRUG_ID)).willReturn(Optional.of(otcDrug));
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));
        // 저장 시 넘긴 엔티티를 그대로 반환 - 실제 JPA save()의 동작을 흉내낸다.
        given(priceReportRepository.save(any(PriceReport.class))).willAnswer(inv -> inv.getArgument(0));
    }

    private PriceReportCreateRequest request(int price, LocalDate purchasedAt, Long receiptFileId) {
        return new PriceReportCreateRequest(PHARMACY_ID, DRUG_ID, price, purchasedAt, receiptFileId, null);
    }

    @Test
    void create_약국이없으면_PharmacyNotFoundException() {
        given(pharmacyRepository.findById(PHARMACY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER_ID, request(2800, null, null)))
            .isInstanceOf(PharmacyNotFoundException.class);
        verify(priceReportRepository, never()).save(any());
    }

    @Test
    void create_약품이없으면_DrugNotFoundException() {
        given(drugRepository.findById(DRUG_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER_ID, request(2800, null, null)))
            .isInstanceOf(DrugNotFoundException.class);
        verify(priceReportRepository, never()).save(any());
    }

    @Test
    void create_전문의약품이면_DrugNotOtcException() {
        Drug prescriptionDrug = Drug.builder().id(DRUG_ID).name("전문약").displayName("전문약")
            .category("기타").packageUnit("1정").otcFlag(false).build();
        given(drugRepository.findById(DRUG_ID)).willReturn(Optional.of(prescriptionDrug));

        assertThatThrownBy(() -> service.create(USER_ID, request(2800, null, null)))
            .isInstanceOf(DrugNotOtcException.class);
        verify(priceReportRepository, never()).save(any());
    }

    @Test
    void create_purchasedAt이미래면_InvalidDateRangeException() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> service.create(USER_ID, request(2800, tomorrow, null)))
            .isInstanceOf(InvalidDateRangeException.class);
        verify(priceReportRepository, never()).save(any());
    }

    @Test
    void create_purchasedAt이180일초과과거면_InvalidDateRangeException() {
        LocalDate tooOld = LocalDate.now().minusDays(181);

        assertThatThrownBy(() -> service.create(USER_ID, request(2800, tooOld, null)))
            .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void create_purchasedAt이정확히180일전이면_경계값이라허용된다() {
        LocalDate exactlyBoundary = LocalDate.now().minusDays(180);

        PriceReportResponse response = service.create(USER_ID, request(2800, exactlyBoundary, null));

        assertThat(response.purchasedAt()).isEqualTo(exactlyBoundary);
    }

    @Test
    void create_purchasedAt미입력이면_오늘로채워진다() {
        PriceReportResponse response = service.create(USER_ID, request(2800, null, null));

        assertThat(response.purchasedAt()).isEqualTo(LocalDate.now());
    }

    @Test
    void create_비교기준중앙값이없으면_가격과무관하게이상치아님() {
        given(priceReportRepository.findDrugWideMedianPrice(DRUG_ID)).willReturn(null);

        PriceReportResponse response = service.create(USER_ID, request(199_999, null, null));
        // price는 검증 범위(100~200000) 안에서 최대값 근처로 설정 - 중앙값이 없으니 그래도 flagged=false.

        assertThat(response.flagged()).isFalse();
        assertThat(response.flagReason()).isNull();
        assertThat(response.warning()).isNull();
    }

    @Test
    void create_중앙값의0_3배미만이면_OUTLIER_LOW로플래그되고경고문구가담긴다() {
        given(priceReportRepository.findDrugWideMedianPrice(DRUG_ID)).willReturn(10_000);

        PriceReportResponse response = service.create(USER_ID, request(2_999, null, null)); // 10000*0.3=3000 미만

        assertThat(response.flagged()).isTrue();
        assertThat(response.flagReason()).isEqualTo(FlagReason.OUTLIER_LOW);
        assertThat(response.warning()).isNotNull();
    }

    @Test
    void create_중앙값의3배초과이면_OUTLIER_HIGH로플래그되고경고문구가담긴다() {
        given(priceReportRepository.findDrugWideMedianPrice(DRUG_ID)).willReturn(10_000);

        PriceReportResponse response = service.create(USER_ID, request(30_001, null, null)); // 10000*3=30000 초과

        assertThat(response.flagged()).isTrue();
        assertThat(response.flagReason()).isEqualTo(FlagReason.OUTLIER_HIGH);
        assertThat(response.warning()).isNotNull();
    }

    @Test
    void create_중앙값의0_3배와3배는경계값이라이상치가아니다() {
        given(priceReportRepository.findDrugWideMedianPrice(DRUG_ID)).willReturn(10_000);

        PriceReportResponse low = service.create(USER_ID, request(3_000, null, null)); // 정확히 10000*0.3
        PriceReportResponse high = service.create(USER_ID, request(30_000, null, null)); // 정확히 10000*3

        assertThat(low.flagged()).isFalse();
        assertThat(high.flagged()).isFalse();
    }

    @Test
    void create_성공하면_report_count가1증가한다() {
        int before = user.getReportCount();

        service.create(USER_ID, request(2800, null, null));

        assertThat(user.getReportCount()).isEqualTo(before + 1);
    }

    @Test
    void create_이상치여도_flagged여부와무관하게항상recalculate를호출한다() {
        given(priceReportRepository.findDrugWideMedianPrice(DRUG_ID)).willReturn(10_000);

        service.create(USER_ID, request(2_999, null, null)); // 이상치

        verify(priceStatService).recalculate(PHARMACY_ID, DRUG_ID);
    }

    @Test
    void create_recalculate가비어있으면_updatedStat이null이다() {
        given(priceStatService.recalculate(PHARMACY_ID, DRUG_ID)).willReturn(Optional.empty());

        PriceReportResponse response = service.create(USER_ID, request(2800, null, null));

        assertThat(response.updatedStat()).isNull();
    }

    @Test
    void create_recalculate결과가있으면_updatedStat에그대로매핑된다() {
        PharmacyDrugPriceStat stat = PharmacyDrugPriceStat.builder()
            .id(1L).pharmacy(pharmacy).drug(otcDrug)
            .repPrice(2800).minPrice(2700).maxPrice(2900).avgPrice(2810).reportCount(5)
            .lastReportedAt(LocalDate.of(2026, 9, 14)).calculatedAt(OffsetDateTime.now())
            .build();
        given(priceStatService.recalculate(PHARMACY_ID, DRUG_ID)).willReturn(Optional.of(stat));

        PriceReportResponse response = service.create(USER_ID, request(2800, null, null));

        assertThat(response.updatedStat()).isNotNull();
        assertThat(response.updatedStat().repPrice()).isEqualTo(2800);
        assertThat(response.updatedStat().reportCount()).isEqualTo(5);
        assertThat(response.updatedStat().lastReportedAt()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void create_receiptFileId가없으면_uploadedFileRepository를조회하지않는다() {
        service.create(USER_ID, request(2800, null, null));

        verify(uploadedFileRepository, never()).findById(any());
    }

    @Test
    void create_receiptFileId가가리키는파일이없으면_예외없이조용히무시한다() {
        given(uploadedFileRepository.findById(999L)).willReturn(Optional.empty());

        PriceReportResponse response = service.create(USER_ID, request(2800, null, 999L));

        assertThat(response).isNotNull();
        verify(uploadedFileRepository).findById(999L);
    }

    @Test
    void create_receiptFileId가가리키는파일이있으면_연결된다() {
        UploadedFile file = UploadedFile.builder().id(5L).originalName("receipt.jpg")
            .storedPath("/2026/09/uuid.jpg").contentType("image/jpeg").sizeBytes(1024L).build();
        given(uploadedFileRepository.findById(5L)).willReturn(Optional.of(file));

        service.create(USER_ID, request(2800, null, 5L));

        verify(priceReportRepository).save(argThatReceiptFileIdIs(5L));
    }

    private PriceReport argThatReceiptFileIdIs(long fileId) {
        return org.mockito.ArgumentMatchers.argThat(report ->
            report.getReceiptFile() != null && report.getReceiptFile().getId() == fileId);
    }

    @Test
    void create_유니크인덱스위반이면_DataIntegrityViolationException이그대로전파되고report_count는증가하지않는다() {
        given(priceReportRepository.save(any(PriceReport.class)))
            .willThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        int before = user.getReportCount();

        assertThatThrownBy(() -> service.create(USER_ID, request(2800, null, null)))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(user.getReportCount()).isEqualTo(before);
        verify(priceStatService, never()).recalculate(eq(PHARMACY_ID), eq(DRUG_ID));
    }
}
