package com.pharmaprice.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.common.exception.InvalidRequestException;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse.DrugPriceItem;
import com.pharmaprice.pharmacy.dto.PharmacySummaryResponse;
import com.pharmaprice.pharmacy.dto.PriceHistoryResponse;
import com.pharmaprice.pharmacy.dto.PriceHistoryResponse.PricePoint;
import com.pharmaprice.pharmacy.repository.PharmacyQueryRepository;
import com.pharmaprice.pharmacy.repository.PharmacyQueryRepository.DrugPriceRow;
import com.pharmaprice.pharmacy.repository.PharmacyQueryRepository.PharmacyRow;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.exception.PharmacyNotFoundException;
import com.pharmaprice.recommendation.distance.DistanceCalculator;
import com.pharmaprice.recommendation.distance.HaversineDistanceCalculator;
import com.pharmaprice.recommendation.exception.InvalidCoordinateException;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportStatus;
import com.pharmaprice.report.repository.PriceReportRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code docs/ROADMAP.md} T-19 서비스 로직 검증. {@code PharmacyQueryRepository}/
 * {@code PharmacyRepository}는 Mockito 목이지만, {@link DistanceCalculator}는
 * {@code SearchServiceImplTest}와 동일하게 실제 {@link HaversineDistanceCalculator}를
 * 그대로 써서 바운딩박스+거리 필터링이 실제로 맞물려 동작하는지 확인한다.
 */
class PharmacyServiceImplTest {

    private final PharmacyQueryRepository pharmacyQueryRepository = mock(PharmacyQueryRepository.class);
    private final PharmacyRepository pharmacyRepository = mock(PharmacyRepository.class);
    private final DistanceCalculator distanceCalculator = new HaversineDistanceCalculator();
    private final PriceReportRepository priceReportRepository = mock(PriceReportRepository.class);

    private PharmacyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PharmacyServiceImpl(pharmacyQueryRepository, pharmacyRepository, distanceCalculator, priceReportRepository);
    }

    private PharmacyRow row(long id, String name, double lat, double lng, String regionCode) {
        PharmacyRow row = mock(PharmacyRow.class);
        given(row.getId()).willReturn(id);
        given(row.getName()).willReturn(name);
        given(row.getAddressRoad()).willReturn("주소" + id);
        given(row.getLat()).willReturn(lat);
        given(row.getLng()).willReturn(lng);
        given(row.getPhone()).willReturn("02-000-0000");
        given(row.getRegionCode()).willReturn(regionCode);
        given(row.getSido()).willReturn(regionCode == null ? null : "서울특별시");
        given(row.getSigungu()).willReturn(regionCode == null ? null : "강남구");
        return row;
    }

    @Test
    void listThrowsWhenNoQueryOrLocation() {
        assertThatThrownBy(() -> service.list(null, null, null, null, 0, 20))
            .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.list("  ", null, null, null, 0, 20))
            .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void listByQueryClampsSizeAndDelegatesToRepository() {
        // row()가 내부적으로 given().willReturn() 체인을 쓰므로, willReturn(...)의
        // 인자 자리에서 바로 호출하면 바깥쪽 given(...) 스텁 진행 중 상태와 겹쳐
        // Mockito가 UnfinishedStubbingException을 던진다 — 미리 로컬 변수로 평가한다.
        PharmacyRow gaon = row(1L, "가온약국", 37.5, 127.0, "11680");
        given(pharmacyQueryRepository.searchByQuery(eq("가온"), eq(50), eq(0L))).willReturn(List.of(gaon));
        given(pharmacyQueryRepository.countByQuery("가온")).willReturn(1L);

        PageResponse<PharmacySummaryResponse> result = service.list("가온", null, null, null, 0, 999);

        assertThat(result.size()).isEqualTo(50); // size clamp
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).distanceM()).isNull(); // 위치 없으니 distanceM null
        assertThat(result.content().get(0).region().sido()).isEqualTo("서울특별시");
        verify(pharmacyQueryRepository, never()).findCandidatesNearby(
            any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void listNearbyFiltersOutCandidatesBeyondRadiusAndSortsByDistance() {
        // 가까운 약국(inRadius)과 바운딩박스 모서리 밖(outOfRadius, 정사각형 안이지만 원 밖)을 함께 반환하도록 목킹.
        PharmacyRow near = row(1L, "가까운약국", 37.5006, 127.0366, "11680"); // 약 850m
        PharmacyRow far = row(2L, "먼약국", 37.51, 127.05, null); // 바운딩박스 모서리 근처, 반경 밖
        given(pharmacyQueryRepository.findCandidatesNearby(
            eq((String) null), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .willReturn(List.of(far, near));

        PageResponse<PharmacySummaryResponse> result = service.list(null, 37.4979, 127.0276, 1000, 0, 20);

        assertThat(result.content()).extracting(PharmacySummaryResponse::id).containsExactly(1L);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).distanceM()).isNotNull();
    }

    @Test
    void listNearbyValidatesCoordinateRange() {
        assertThatThrownBy(() -> service.list(null, 10.0, 200.0, 1000, 0, 20))
            .isInstanceOf(InvalidCoordinateException.class);
    }

    @Test
    void getDetailThrows404WhenPharmacyMissing() {
        given(pharmacyRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(999L, null, null))
            .isInstanceOf(PharmacyNotFoundException.class);
    }

    @Test
    void getDetailThrows404WhenPharmacyInactive() {
        Pharmacy inactive = Pharmacy.builder().id(1L).name("폐업약국").lat(37.5).lng(127.0).active(false).build();
        given(pharmacyRepository.findById(1L)).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.getDetail(1L, null, null))
            .isInstanceOf(PharmacyNotFoundException.class);
    }

    @Test
    void getDetailComputesDistanceMWhenLatLngGivenAndNullOtherwise() {
        Region region = Region.builder().code("11680").sido("서울특별시").sigungu("강남구")
            .centerLat(37.4979).centerLng(127.0276).build();
        Pharmacy pharmacy = Pharmacy.builder().id(1L).name("가온약국").region(region)
            .lat(37.5006).lng(127.0366).active(true).build();
        given(pharmacyRepository.findById(1L)).willReturn(Optional.of(pharmacy));
        given(pharmacyQueryRepository.findDrugPrices(1L)).willReturn(List.of());

        PharmacyDetailResponse withoutLocation = service.getDetail(1L, null, null);
        PharmacyDetailResponse withLocation = service.getDetail(1L, 37.4979, 127.0276);

        assertThat(withoutLocation.distanceM()).isNull();
        assertThat(withLocation.distanceM()).isNotNull().isGreaterThan(0);
        assertThat(withLocation.region().code()).isEqualTo("11680");
    }

    @Test
    void getDetailMapsDrugPricesWithDiffFromNationalAvgKeepingRepositoryOrder() {
        Pharmacy pharmacy = Pharmacy.builder().id(1L).name("가온약국").lat(37.5).lng(127.0).active(true).build();
        given(pharmacyRepository.findById(1L)).willReturn(Optional.of(pharmacy));

        DrugPriceRow cheap = drugPriceRow(1L, "저가약", 2000, 2500);
        DrugPriceRow noNationalAvg = drugPriceRow(2L, "통계없음약", 3000, null);
        given(pharmacyQueryRepository.findDrugPrices(1L)).willReturn(List.of(cheap, noNationalAvg));

        PharmacyDetailResponse detail = service.getDetail(1L, null, null);

        assertThat(detail.drugPrices()).extracting(DrugPriceItem::drugId).containsExactly(1L, 2L);
        assertThat(detail.drugPrices().get(0).diffFromNationalAvg()).isEqualTo(-500); // 2000-2500
        assertThat(detail.drugPrices().get(1).diffFromNationalAvg()).isNull();
    }

    @Test
    void getPriceHistoryIncludesFlaggedPointsAndReturnsEmptyWhenNoReports() {
        PriceReport normal = report(2700, LocalDate.of(2026, 6, 2), false);
        PriceReport flagged = report(9900, LocalDate.of(2026, 8, 1), true);
        given(priceReportRepository.findByPharmacy_IdAndDrug_IdAndStatusNotAndPurchasedAtGreaterThanEqualOrderByPurchasedAtAsc(
            eq(101L), eq(1L), eq(ReportStatus.HIDDEN), any(LocalDate.class)))
            .willReturn(List.of(normal, flagged));

        PriceHistoryResponse withData = service.getPriceHistory(101L, 1L, null);

        assertThat(withData.pharmacyId()).isEqualTo(101L);
        assertThat(withData.drugId()).isEqualTo(1L);
        assertThat(withData.points()).extracting(PricePoint::flagged).containsExactly(false, true); // flagged여도 제외되지 않음

        given(priceReportRepository.findByPharmacy_IdAndDrug_IdAndStatusNotAndPurchasedAtGreaterThanEqualOrderByPurchasedAtAsc(
            eq(999L), eq(999L), eq(ReportStatus.HIDDEN), any(LocalDate.class)))
            .willReturn(List.of());

        PriceHistoryResponse empty = service.getPriceHistory(999L, 999L, null);

        assertThat(empty.points()).isEmpty(); // 0건이어도 예외 없이 빈 리스트 (컨트롤러에서 200으로 응답)
    }

    @Test
    void getPriceHistoryExcludesOnlyHiddenStatusAtRepositoryLevel() {
        // HIDDEN 제외는 derived query의 StatusNot 조건이 DB에서 처리한다 — 서비스는
        // 필터링하지 않고 excludedStatus 인자로 HIDDEN을 넘기기만 한다. 실제 DB 필터링
        // 동작(REJECTED/ACTIVE는 포함) 검증은 리포지토리 계층 몫이며, 여기서는 서비스가
        // 항상 HIDDEN을 넘기는지만 확인한다.
        given(priceReportRepository.findByPharmacy_IdAndDrug_IdAndStatusNotAndPurchasedAtGreaterThanEqualOrderByPurchasedAtAsc(
            any(), any(), any(), any())).willReturn(List.of());

        service.getPriceHistory(101L, 1L, 30);

        verify(priceReportRepository).findByPharmacy_IdAndDrug_IdAndStatusNotAndPurchasedAtGreaterThanEqualOrderByPurchasedAtAsc(
            eq(101L), eq(1L), eq(ReportStatus.HIDDEN), any(LocalDate.class));
    }

    @Test
    void getPriceHistoryClampsDaysToDefaultAndMax() {
        given(priceReportRepository.findByPharmacy_IdAndDrug_IdAndStatusNotAndPurchasedAtGreaterThanEqualOrderByPurchasedAtAsc(
            any(), any(), any(), any())).willReturn(List.of());

        service.getPriceHistory(101L, 1L, null); // 기본 180일
        service.getPriceHistory(101L, 1L, 9999); // 최대 365일로 clamp
        service.getPriceHistory(101L, 1L, 30); // 그대로 30일

        verify(priceReportRepository).findByPharmacy_IdAndDrug_IdAndStatusNotAndPurchasedAtGreaterThanEqualOrderByPurchasedAtAsc(
            eq(101L), eq(1L), eq(ReportStatus.HIDDEN), eq(LocalDate.now().minusDays(180)));
        verify(priceReportRepository).findByPharmacy_IdAndDrug_IdAndStatusNotAndPurchasedAtGreaterThanEqualOrderByPurchasedAtAsc(
            eq(101L), eq(1L), eq(ReportStatus.HIDDEN), eq(LocalDate.now().minusDays(365)));
        verify(priceReportRepository).findByPharmacy_IdAndDrug_IdAndStatusNotAndPurchasedAtGreaterThanEqualOrderByPurchasedAtAsc(
            eq(101L), eq(1L), eq(ReportStatus.HIDDEN), eq(LocalDate.now().minusDays(30)));
    }

    private PriceReport report(int price, LocalDate purchasedAt, boolean flagged) {
        return PriceReport.builder().price(price).purchasedAt(purchasedAt).flagged(flagged).build();
    }

    private DrugPriceRow drugPriceRow(long drugId, String displayName, int repPrice, Integer nationalAvg) {
        DrugPriceRow row = mock(DrugPriceRow.class);
        given(row.getDrugId()).willReturn(drugId);
        given(row.getDisplayName()).willReturn(displayName);
        given(row.getPackageUnit()).willReturn("1개");
        given(row.getRepPrice()).willReturn(repPrice);
        given(row.getMinPrice()).willReturn(repPrice);
        given(row.getMaxPrice()).willReturn(repPrice);
        given(row.getAvgPrice()).willReturn(repPrice);
        given(row.getReportCount()).willReturn(3);
        given(row.getLastReportedAt()).willReturn(LocalDate.now());
        given(row.getNationalAvg()).willReturn(nationalAvg);
        return row;
    }
}
