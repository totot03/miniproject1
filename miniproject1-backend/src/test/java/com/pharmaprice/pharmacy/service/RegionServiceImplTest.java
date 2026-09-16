package com.pharmaprice.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.pharmaprice.pharmacy.dto.RegionGroupResponse;
import com.pharmaprice.pharmacy.repository.RegionQueryRepository;
import com.pharmaprice.pharmacy.repository.RegionQueryRepository.RegionRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code RegionQueryRepository}가 이미 정렬해 내려준 평면 목록을 시도별로
 * 묶는 순수 로직만 검증한다. DB 접근이 없으므로 {@code RegionRow}는
 * Mockito로 스텁한다({@code RegionQueryRepositoryTest}가 실제 native 쿼리를
 * 담당).
 */
@ExtendWith(MockitoExtension.class)
class RegionServiceImplTest {

    @Mock
    private RegionQueryRepository regionQueryRepository;

    private RegionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RegionServiceImpl(regionQueryRepository);
    }

    private RegionRow row(String code, String sido, String sigungu, long pharmacyCount) {
        RegionRow row = Mockito.mock(RegionRow.class);
        given(row.getCode()).willReturn(code);
        given(row.getSido()).willReturn(sido);
        given(row.getSigungu()).willReturn(sigungu);
        given(row.getCenterLat()).willReturn(37.5);
        given(row.getCenterLng()).willReturn(127.0);
        given(row.getPharmacyCount()).willReturn(pharmacyCount);
        return row;
    }

    @Test
    void groupsRowsWithSameSidoIntoOneEntry() {
        // row()가 내부적으로 given().willReturn() 체인을 쓰므로, willReturn(...)의
        // 인자 자리에서 바로 호출하면 바깥쪽 given(...) 스텁 진행 중 상태와 겹쳐
        // Mockito가 UnfinishedStubbingException을 던진다 — 미리 로컬 변수로 평가한다.
        List<RegionRow> rows = List.of(
            row("11680", "서울특별시", "강남구", 42),
            row("11350", "서울특별시", "노원구", 0)
        );
        given(regionQueryRepository.listWithPharmacyCount()).willReturn(rows);

        List<RegionGroupResponse> result = service.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sido()).isEqualTo("서울특별시");
        assertThat(result.get(0).sigungus()).extracting(RegionGroupResponse.SigunguItem::sigungu)
            .containsExactly("강남구", "노원구");
        assertThat(result.get(0).sigungus()).extracting(RegionGroupResponse.SigunguItem::pharmacyCount)
            .containsExactly(42L, 0L);
    }

    @Test
    void preservesRepositoryOrderAcrossDifferentSidos() {
        // 리포지토리가 sido, sigungu 순으로 이미 정렬해 반환한다는 전제 — 그 순서가
        // groupingBy(HashMap 기본값)로 뒤섞이지 않고 그대로 보존되는지 확인한다.
        List<RegionRow> rows = List.of(
            row("41135", "경기도", "성남시", 10),
            row("11680", "서울특별시", "강남구", 42)
        );
        given(regionQueryRepository.listWithPharmacyCount()).willReturn(rows);

        List<RegionGroupResponse> result = service.list();

        assertThat(result).extracting(RegionGroupResponse::sido)
            .containsExactly("경기도", "서울특별시");
    }
}
