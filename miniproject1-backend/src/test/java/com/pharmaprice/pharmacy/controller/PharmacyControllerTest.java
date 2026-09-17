package com.pharmaprice.pharmacy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse.DrugPriceItem;
import com.pharmaprice.pharmacy.dto.PharmacySummaryResponse;
import com.pharmaprice.pharmacy.dto.PriceHistoryResponse;
import com.pharmaprice.pharmacy.dto.PriceHistoryResponse.PricePoint;
import com.pharmaprice.pharmacy.dto.RegionInfo;
import com.pharmaprice.pharmacy.service.PharmacyService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code docs/API.md} §4 응답 형태와 상태코드를 검증하는 컨트롤러 슬라이스 테스트.
 * {@code DrugControllerTest}와 동일하게 {@code SecurityConfig} 부재(T-23 몫)로 인한
 * 보안 오토컨피그 4종을 명시적으로 제외한다.
 */
@WebMvcTest(controllers = PharmacyController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class
})
class PharmacyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PharmacyService pharmacyService;

    @Test
    void listReturns200WithApiSpecFields() throws Exception {
        RegionInfo region = new RegionInfo("11680", "서울특별시", "강남구");
        PharmacySummaryResponse summary = new PharmacySummaryResponse(
            101L, "가온약국", "서울특별시 강남구 테헤란로 123", 37.5012, 127.0396, "02-555-1234", 340L, region);
        given(pharmacyService.list(eq("가온"), any(), any(), any(), anyInt(), anyInt()))
            .willReturn(PageResponse.of(List.of(summary), 0, 20, 1));

        mockMvc.perform(get("/api/v1/pharmacies").param("q", "가온").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(101))
            .andExpect(jsonPath("$.content[0].name").value("가온약국"))
            .andExpect(jsonPath("$.content[0].addressRoad").value("서울특별시 강남구 테헤란로 123"))
            .andExpect(jsonPath("$.content[0].lat").value(37.5012))
            .andExpect(jsonPath("$.content[0].lng").value(127.0396))
            .andExpect(jsonPath("$.content[0].phone").value("02-555-1234"))
            .andExpect(jsonPath("$.content[0].distanceM").value(340))
            .andExpect(jsonPath("$.content[0].region.code").value("11680"))
            .andExpect(jsonPath("$.content[0].region.sido").value("서울특별시"))
            .andExpect(jsonPath("$.content[0].region.sigungu").value("강남구"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listDelegatesQueryParamsToServiceWithDefaults() throws Exception {
        given(pharmacyService.list(any(), any(), any(), any(), anyInt(), anyInt()))
            .willReturn(PageResponse.of(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/pharmacies")
                .param("lat", "37.5").param("lng", "127.0").param("radius", "3000"))
            .andExpect(status().isOk());

        // page/size 기본값과 검증 책임 위임 여부만 확인한다 — clamp·검증은 서비스 몫.
        verify(pharmacyService).list(eq(null), eq(37.5), eq(127.0), eq(3000), eq(0), eq(20));
    }

    @Test
    void listReturns400WhenServiceRejectsMissingQueryAndLocation() throws Exception {
        given(pharmacyService.list(eq(null), eq(null), eq(null), any(), anyInt(), anyInt()))
            .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "q 또는 lat/lng 중 하나는 필수입니다."));

        mockMvc.perform(get("/api/v1/pharmacies"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void detailReturns200WithBusinessHoursRegionAndDrugPrices() throws Exception {
        RegionInfo region = new RegionInfo("11680", "서울특별시", "강남구");
        DrugPriceItem drugPrice = new DrugPriceItem(
            1L, "타이레놀 500mg", "8정", 2800, 2700, 3000, 2830, 4, LocalDate.of(2026, 9, 10), 3120, -320);
        PharmacyDetailResponse detail = new PharmacyDetailResponse(
            101L, "가온약국", "서울특별시 강남구 테헤란로 123", "서울특별시 강남구 역삼동 823",
            37.5012, 127.0396, "02-555-1234",
            Map.of("mon", List.of("09:00", "19:00"), "sun", List.of()),
            340L, region, List.of(drugPrice));
        given(pharmacyService.getDetail(101L, 37.5, 127.0)).willReturn(detail);

        mockMvc.perform(get("/api/v1/pharmacies/{pharmacyId}", 101L)
                .param("lat", "37.5").param("lng", "127.0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(jsonPath("$.name").value("가온약국"))
            .andExpect(jsonPath("$.addressJibun").value("서울특별시 강남구 역삼동 823"))
            .andExpect(jsonPath("$.businessHours.mon[0]").value("09:00"))
            .andExpect(jsonPath("$.region.sigungu").value("강남구"))
            .andExpect(jsonPath("$.drugPrices[0].drugId").value(1))
            .andExpect(jsonPath("$.drugPrices[0].repPrice").value(2800))
            .andExpect(jsonPath("$.drugPrices[0].nationalAvgPrice").value(3120))
            .andExpect(jsonPath("$.drugPrices[0].diffFromNationalAvg").value(-320));
    }

    @Test
    void detailReturns404WhenPharmacyNotFound() throws Exception {
        given(pharmacyService.getDetail(999L, null, null))
            .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "pharmacy not found: 999"));

        mockMvc.perform(get("/api/v1/pharmacies/{pharmacyId}", 999L))
            .andExpect(status().isNotFound());
    }

    @Test
    void historyReturns200WithApiSpecFieldsIncludingFlaggedPoint() throws Exception {
        PriceHistoryResponse history = new PriceHistoryResponse(101L, 1L, List.of(
            new PricePoint(LocalDate.of(2026, 6, 2), 2700, false),
            new PricePoint(LocalDate.of(2026, 8, 1), 9900, true)));
        given(pharmacyService.getPriceHistory(eq(101L), eq(1L), any())).willReturn(history);

        mockMvc.perform(get("/api/v1/pharmacies/{pharmacyId}/drugs/{drugId}/history", 101L, 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pharmacyId").value(101))
            .andExpect(jsonPath("$.drugId").value(1))
            .andExpect(jsonPath("$.points[0].purchasedAt").value("2026-06-02"))
            .andExpect(jsonPath("$.points[0].flagged").value(false))
            .andExpect(jsonPath("$.points[1].price").value(9900))
            .andExpect(jsonPath("$.points[1].flagged").value(true));
    }

    @Test
    void historyReturns200WithEmptyPointsWhenNoHistory() throws Exception {
        given(pharmacyService.getPriceHistory(eq(101L), eq(1L), any()))
            .willReturn(new PriceHistoryResponse(101L, 1L, List.of()));

        mockMvc.perform(get("/api/v1/pharmacies/{pharmacyId}/drugs/{drugId}/history", 101L, 1L))
            .andExpect(status().isOk()) // 이력 0건이어도 404가 아니라 200
            .andExpect(jsonPath("$.points").isArray())
            .andExpect(jsonPath("$.points").isEmpty());
    }

    @Test
    void historyDelegatesDaysParamToService() throws Exception {
        given(pharmacyService.getPriceHistory(eq(101L), eq(1L), eq(30)))
            .willReturn(new PriceHistoryResponse(101L, 1L, List.of()));

        mockMvc.perform(get("/api/v1/pharmacies/{pharmacyId}/drugs/{drugId}/history", 101L, 1L).param("days", "30"))
            .andExpect(status().isOk());

        // days 클램프(기본180/최대365)는 서비스 몫 — 컨트롤러는 파라미터를 그대로 전달만 한다.
        verify(pharmacyService).getPriceHistory(101L, 1L, 30);
    }
}
