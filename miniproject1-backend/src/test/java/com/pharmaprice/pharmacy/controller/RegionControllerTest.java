package com.pharmaprice.pharmacy.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pharmaprice.pharmacy.dto.RegionGroupResponse;
import com.pharmaprice.pharmacy.dto.RegionGroupResponse.SigunguItem;
import com.pharmaprice.pharmacy.service.RegionService;
import java.util.List;
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
 * {@code docs/API.md} §7 응답 형태와 {@code Cache-Control} 헤더를 검증하는
 * 컨트롤러 슬라이스 테스트. {@code SecurityConfig}가 아직 없어(T-23 몫)
 * {@code DrugControllerTest}와 동일한 이유로 보안 오토컨피그 4종을 제외한다.
 */
@WebMvcTest(controllers = RegionController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class
})
class RegionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionService regionService;

    @Test
    void listReturns200WithApiSpecFields() throws Exception {
        RegionGroupResponse group = new RegionGroupResponse(
            "서울특별시",
            List.of(new SigunguItem("11680", "강남구", 37.4959, 127.0664, 42L)));
        given(regionService.list()).willReturn(List.of(group));

        mockMvc.perform(get("/api/v1/regions").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sido").value("서울특별시"))
            .andExpect(jsonPath("$[0].sigungus[0].code").value("11680"))
            .andExpect(jsonPath("$[0].sigungus[0].sigungu").value("강남구"))
            .andExpect(jsonPath("$[0].sigungus[0].centerLat").value(37.4959))
            .andExpect(jsonPath("$[0].sigungus[0].centerLng").value(127.0664))
            .andExpect(jsonPath("$[0].sigungus[0].pharmacyCount").value(42));
    }

    @Test
    void listIsNotWrappedInPageResponse() throws Exception {
        given(regionService.list()).willReturn(List.of());

        // API.md §7: 페이지네이션 없음 — 최상위가 content/page 래퍼가 아니라 배열이어야 한다.
        mockMvc.perform(get("/api/v1/regions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listSetsOneHourCacheControlHeader() throws Exception {
        given(regionService.list()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/regions"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("max-age=3600")));
    }
}
