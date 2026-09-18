package com.pharmaprice.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pharmaprice.admin.dto.AdminPriceGapResponse;
import com.pharmaprice.admin.exception.DrugNotFoundException;
import com.pharmaprice.admin.repository.AdminStatsQueryRepository;
import com.pharmaprice.admin.repository.AdminStatsQueryRepository.PriceGapRow;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.recommendation.repository.PriceStatRepository;
import com.pharmaprice.report.repository.PriceReportRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code docs/ROADMAP.md} T-31 {@link AdminStatsServiceImpl} 비즈니스 로직 검증.
 * 리포지토리는 전부 Mockito 목이다 - native SQL 자체의 정확성은
 * {@code AdminStatsQueryRepository}를 실 DB로 직접 실행해 확인했다(관련 커밋 참고).
 */
class AdminStatsServiceImplTest {

    private final PharmacyRepository pharmacyRepository = mock(PharmacyRepository.class);
    private final DrugRepository drugRepository = mock(DrugRepository.class);
    private final PriceReportRepository priceReportRepository = mock(PriceReportRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final PriceStatRepository priceStatRepository = mock(PriceStatRepository.class);
    private final AdminStatsQueryRepository adminStatsQueryRepository = mock(AdminStatsQueryRepository.class);

    private AdminStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminStatsServiceImpl(
            pharmacyRepository, drugRepository, priceReportRepository, appUserRepository,
            priceStatRepository, adminStatsQueryRepository);
        given(adminStatsQueryRepository.recentReportTrend()).willReturn(List.of());
    }

    @Test
    void overview_정상_커버리지비율_계산() {
        given(pharmacyRepository.count()).willReturn(10L);
        given(drugRepository.count()).willReturn(5L);
        given(priceReportRepository.count()).willReturn(100L);
        given(appUserRepository.count()).willReturn(20L);
        given(priceStatRepository.count()).willReturn(25L);
        given(priceReportRepository.countByFlaggedTrue()).willReturn(3L);

        var overview = service.overview();

        assertThat(overview.totals().pharmacyCount()).isEqualTo(10L);
        assertThat(overview.totals().coveredPairCount()).isEqualTo(25L);
        assertThat(overview.flaggedReportCount()).isEqualTo(3L);
        assertThat(overview.coverageRate()).isEqualTo(25.0 / (10 * 5));
    }

    @Test
    void overview_약국이나약품이0건이면_커버리지비율은0이고_0으로나누기예외없음() {
        given(pharmacyRepository.count()).willReturn(0L);
        given(drugRepository.count()).willReturn(5L);
        given(priceReportRepository.count()).willReturn(0L);
        given(appUserRepository.count()).willReturn(0L);
        given(priceStatRepository.count()).willReturn(0L);
        given(priceReportRepository.countByFlaggedTrue()).willReturn(0L);

        var overview = service.overview();

        assertThat(overview.coverageRate()).isZero();
    }

    @Test
    void drugStats_약품이없으면_DrugNotFoundException() {
        given(drugRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.drugStats(99L))
            .isInstanceOf(DrugNotFoundException.class);
    }

    @Test
    void priceGaps_limit이0이면_1로clamp되어리포지토리호출() {
        given(adminStatsQueryRepository.priceGaps(1)).willReturn(List.of());

        service.priceGaps(0);

        verify(adminStatsQueryRepository).priceGaps(1);
    }

    @Test
    void priceGaps_limit이100이면_50으로clamp되어리포지토리호출() {
        given(adminStatsQueryRepository.priceGaps(50)).willReturn(List.of());

        service.priceGaps(100);

        verify(adminStatsQueryRepository).priceGaps(50);
    }

    @Test
    void priceGaps_정상_행이응답DTO로매핑된다() {
        PriceGapRow row = mock(PriceGapRow.class);
        given(row.getDrugId()).willReturn(7L);
        given(row.getDrugDisplayName()).willReturn("겔포스엠 현탁액");
        given(row.getCheapestSido()).willReturn("서울특별시");
        given(row.getCheapestSigungu()).willReturn("노원구");
        given(row.getCheapestAvg()).willReturn(4200);
        given(row.getPriciestSido()).willReturn("서울특별시");
        given(row.getPriciestSigungu()).willReturn("서초구");
        given(row.getPriciestAvg()).willReturn(6800);
        given(row.getGap()).willReturn(2600);
        given(row.getGapPct()).willReturn(61.9);
        given(adminStatsQueryRepository.priceGaps(10)).willReturn(List.of(row));

        AdminPriceGapResponse response = service.priceGaps(10);

        assertThat(response.rows()).hasSize(1);
        AdminPriceGapResponse.Row mapped = response.rows().get(0);
        assertThat(mapped.drug().id()).isEqualTo(7L);
        assertThat(mapped.cheapestRegion().sigungu()).isEqualTo("노원구");
        assertThat(mapped.priciestRegion().avgPrice()).isEqualTo(6800);
        assertThat(mapped.gap()).isEqualTo(2600);
        assertThat(mapped.gapPct()).isEqualTo(61.9);
    }
}
