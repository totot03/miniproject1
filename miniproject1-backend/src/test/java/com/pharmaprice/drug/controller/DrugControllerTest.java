package com.pharmaprice.drug.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.drug.dto.DrugDetailResponse;
import com.pharmaprice.drug.dto.DrugDetailResponse.PriceStats;
import com.pharmaprice.drug.dto.DrugSummaryResponse;
import com.pharmaprice.drug.exception.DrugNotFoundException;
import com.pharmaprice.drug.service.DrugService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code docs/API.md} §3 응답 형태와 상태코드를 검증하는 컨트롤러 슬라이스 테스트.
 *
 * <p>{@code SecurityConfig}가 아직 없어(T-23 몫) 기본 Spring Security가
 * 전 요청에 인증을 요구한다. 이 프로젝트 pom.xml은 {@code spring-boot-starter-security-test}를
 * 의존성에 두지 않아, {@code @WebMvcTest}가 보안 오토컨피그를 아예 끌어오지
 * 않는다(직접 확인: 이 테스트 실행 로그에 "generated security password" 경고가
 * 없다) — 그래서 지금은 아래 {@code excludeAutoConfiguration}이 사실상 no-op이다.
 * 그래도 명시해두는 이유는, 나중에 다른 테스트가 {@code @WithMockUser} 등을 쓰려고
 * {@code spring-boot-starter-security-test}를 추가하는 순간 이 프로젝트의 모든
 * {@code @WebMvcTest} 슬라이스가 (Spring Boot 4.1.1 jar로 확인한) 보안 오토컨피그
 * 4종을 자동으로 끌어와 401을 내기 시작하기 때문이다 — 그 시점에도 이 테스트가
 * 깨지지 않도록 미리 방어해둔다.</p>
 */
@WebMvcTest(controllers = DrugController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class
})
class DrugControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DrugService drugService;

    @Test
    void searchReturns200WithApiSpecFields() throws Exception {
        DrugSummaryResponse summary = new DrugSummaryResponse(
            1L, "196800050", "타이레놀 500mg", "타이레놀정500밀리그람", "한국얀센",
            "해열진통", "정제", "8정", "https://example.com/img.png", 3120, 184L);
        given(drugService.search(eq("타이레놀"), any(), anyInt(), anyInt()))
            .willReturn(PageResponse.of(java.util.List.of(summary), 0, 20, 1));

        mockMvc.perform(get("/api/v1/drugs").param("q", "타이레놀").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[0].itemSeq").value("196800050"))
            .andExpect(jsonPath("$.content[0].displayName").value("타이레놀 500mg"))
            .andExpect(jsonPath("$.content[0].name").value("타이레놀정500밀리그람"))
            .andExpect(jsonPath("$.content[0].maker").value("한국얀센"))
            .andExpect(jsonPath("$.content[0].category").value("해열진통"))
            .andExpect(jsonPath("$.content[0].form").value("정제"))
            .andExpect(jsonPath("$.content[0].packageUnit").value("8정"))
            .andExpect(jsonPath("$.content[0].nationalAvgPrice").value(3120))
            .andExpect(jsonPath("$.content[0].pharmacyCount").value(184))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void searchDelegatesQueryParamsToService() throws Exception {
        given(drugService.search(any(), any(), anyInt(), anyInt()))
            .willReturn(PageResponse.of(java.util.List.of(), 0, 8, 0));

        mockMvc.perform(get("/api/v1/drugs").param("size", "8"))
            .andExpect(status().isOk());

        // size clamp는 서비스 책임 — 컨트롤러는 파라미터를 그대로 위임만 하는지 확인한다.
        verify(drugService).search(eq(null), eq(null), eq(0), eq(8));
    }

    @Test
    void detailReturns200WithPriceStats() throws Exception {
        DrugDetailResponse detail = new DrugDetailResponse(
            1L, "196800050", "타이레놀 500mg", "타이레놀정500밀리그람", "한국얀센",
            "해열진통", "정제", "8정", "https://example.com/img.png",
            new PriceStats(3120, 2200, 4800, 184L, 412L));
        given(drugService.getDetail(1L)).willReturn(detail);

        mockMvc.perform(get("/api/v1/drugs/{drugId}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.priceStats.nationalAvg").value(3120))
            .andExpect(jsonPath("$.priceStats.nationalMin").value(2200))
            .andExpect(jsonPath("$.priceStats.nationalMax").value(4800))
            .andExpect(jsonPath("$.priceStats.pharmacyCount").value(184))
            .andExpect(jsonPath("$.priceStats.reportCount").value(412));
    }

    @Test
    void detailReturns404WhenDrugNotFoundOrPrescriptionOnly() throws Exception {
        given(drugService.getDetail(999L))
            .willThrow(new DrugNotFoundException("drug not found: 999"));

        mockMvc.perform(get("/api/v1/drugs/{drugId}", 999L))
            .andExpect(status().isNotFound());
    }
}
