package com.pharmaprice.admin.controller;

import com.pharmaprice.admin.dto.AdminDrugStatsResponse;
import com.pharmaprice.admin.dto.AdminPriceGapResponse;
import com.pharmaprice.admin.dto.AdminRegionStatsResponse;
import com.pharmaprice.admin.dto.AdminStatsOverviewResponse;
import com.pharmaprice.admin.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code docs/ROADMAP.md} T-31 / {@code docs/API.md} §8 관리자 전용 통계 4종.
 *
 * <p>{@code SecurityConfig}가 이미 {@code "/api/v1/admin/**"}를 {@code hasRole(ADMIN)}으로
 * 막고 있지만, ROADMAP T-31 5번 항목이 메서드 레벨에도 명시를 요구해 {@code @PreAuthorize}를
 * 이중으로 건다 - URL 패턴 매처가 실수로 지워지더라도 컨트롤러 자체가 여전히 막혀 있도록
 * 하는, 서로 다른 계층의 안전장치다. 이 프로젝트에서 {@code @PreAuthorize} 첫 사용이지만
 * {@code @EnableMethodSecurity}가 {@code SecurityConfig}에 이미 선언돼 있어 별도 설정
 * 없이 동작한다.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminStatsOverviewResponse overview() {
        return adminStatsService.overview();
    }

    @GetMapping("/regions")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminRegionStatsResponse regions(@RequestParam(required = false) String regionCode,
                                             @RequestParam(required = false) Long drugId,
                                             @RequestParam(required = false) String sido) {
        return adminStatsService.regions(regionCode, drugId, sido);
    }

    @GetMapping("/drugs/{drugId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDrugStatsResponse drugStats(@PathVariable Long drugId) {
        return adminStatsService.drugStats(drugId);
    }

    @GetMapping("/price-gaps")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminPriceGapResponse priceGaps(@RequestParam(defaultValue = "10") int limit) {
        return adminStatsService.priceGaps(limit);
    }
}
