package com.pharmaprice.recommendation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pharmaprice.recommendation.dto.Badge;
import com.pharmaprice.recommendation.dto.DataSource;
import com.pharmaprice.recommendation.dto.ScoreBreakdown;
import com.pharmaprice.recommendation.dto.SearchResponse;
import com.pharmaprice.recommendation.dto.SearchResponse.DrugInfo;
import com.pharmaprice.recommendation.dto.SearchResponse.PharmacyInfo;
import com.pharmaprice.recommendation.dto.SearchResponse.PriceInfo;
import com.pharmaprice.recommendation.dto.SearchResponse.QueryInfo;
import com.pharmaprice.recommendation.dto.SearchResponse.SearchResultItem;
import com.pharmaprice.recommendation.dto.SearchResponse.SearchSummary;
import com.pharmaprice.recommendation.service.SearchService;
import java.time.LocalDate;
import java.util.List;
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
 * {@code docs/API.md} §5 응답 형태를 검증하는 컨트롤러 슬라이스 테스트.
 * {@code SecurityConfig}가 아직 없어(T-23 몫) {@code DrugControllerTest}와
 * 동일한 이유로 보안 오토컨피그 4종을 제외한다.
 */
@WebMvcTest(controllers = SearchController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class
})
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    private SearchResponse sampleResponse() {
        ScoreBreakdown breakdown = new ScoreBreakdown(1.0, 0.83, 0.891, new ScoreBreakdown.Weights(0.6, 0.25, 0.15));
        SearchResultItem item = new SearchResultItem(
            1, true,
            new PharmacyInfo(101L, "가온약국", "서울특별시 강남구 테헤란로 123", 37.5012, 127.0396, "02-555-1234"),
            new PriceInfo(2600, 2500, 2640, 540, 4, LocalDate.of(2026, 9, 10), 5),
            340L, 0.9124, breakdown, List.of(Badge.LOWEST_PRICE));
        return new SearchResponse(
            new DrugInfo(1L, "타이레놀 500mg", "8정", "https://example.com/img.png"),
            new QueryInfo(37.4979, 127.0276, 2000, "SCORE", "GPS"),
            new SearchSummary(1, 3140, 2600, 3900, 1300),
            DataSource.SEED,
            List.of(item),
            null);
    }

    @Test
    void searchReturns200WithApiSpecFields() throws Exception {
        given(searchService.search(eq(1L), eq(37.4979), eq(127.0276), any(), eq(2000), eq("SCORE"), eq(20)))
            .willReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/search")
                .param("drugId", "1").param("lat", "37.4979").param("lng", "127.0276")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.drug.id").value(1))
            .andExpect(jsonPath("$.drug.displayName").value("타이레놀 500mg"))
            .andExpect(jsonPath("$.query.locationSource").value("GPS"))
            .andExpect(jsonPath("$.summary.resultCount").value(1))
            .andExpect(jsonPath("$.summary.maxSaving").value(1300))
            .andExpect(jsonPath("$.dataSource").value("SEED"))
            .andExpect(jsonPath("$.results[0].rank").value(1))
            .andExpect(jsonPath("$.results[0].recommended").value(true))
            .andExpect(jsonPath("$.results[0].pharmacy.name").value("가온약국"))
            .andExpect(jsonPath("$.results[0].price.repPrice").value(2600))
            .andExpect(jsonPath("$.results[0].distanceM").value(340))
            .andExpect(jsonPath("$.results[0].score").value(0.9124))
            .andExpect(jsonPath("$.results[0].scoreBreakdown.priceScore").value(1.0))
            .andExpect(jsonPath("$.results[0].badges[0]").value("LOWEST_PRICE"))
            .andExpect(jsonPath("$.suggestion").doesNotExist());
    }

    @Test
    void searchDelegatesQueryParamsToService() throws Exception {
        given(searchService.search(any(), any(), any(), any(), anyInt(), anyString(), anyInt()))
            .willReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/search")
                .param("drugId", "1").param("regionCode", "11680")
                .param("radius", "1000").param("sort", "PRICE").param("limit", "10"))
            .andExpect(status().isOk());

        verify(searchService).search(eq(1L), eq(null), eq(null), eq("11680"), eq(1000), eq("PRICE"), eq(10));
    }

    @Test
    void searchUsesDefaultRadiusSortAndLimitWhenOmitted() throws Exception {
        given(searchService.search(any(), any(), any(), any(), anyInt(), anyString(), anyInt()))
            .willReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/search").param("drugId", "1").param("regionCode", "11680"))
            .andExpect(status().isOk());

        verify(searchService).search(eq(1L), eq(null), eq(null), eq("11680"), eq(2000), eq("SCORE"), eq(20));
    }

    @Test
    void searchReturns404WhenDrugNotFoundOrPrescriptionOnly() throws Exception {
        given(searchService.search(eq(999L), any(), any(), any(), anyInt(), anyString(), anyInt()))
            .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "drug not found: 999"));

        mockMvc.perform(get("/api/v1/search").param("drugId", "999").param("regionCode", "11680"))
            .andExpect(status().isNotFound());
    }

    @Test
    void searchReturns400WhenLocationMissing() throws Exception {
        given(searchService.search(eq(1L), any(), any(), any(), anyInt(), anyString(), anyInt()))
            .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat/lng 또는 regionCode 중 하나는 필수입니다."));

        mockMvc.perform(get("/api/v1/search").param("drugId", "1"))
            .andExpect(status().isBadRequest());
    }
}
