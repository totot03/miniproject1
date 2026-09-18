package com.pharmaprice.admin.controller;

import com.pharmaprice.admin.dto.AdminReportListItemResponse;
import com.pharmaprice.admin.dto.AdminReportUpdateRequest;
import com.pharmaprice.admin.dto.AdminReportUpdateResponse;
import com.pharmaprice.admin.service.AdminReportService;
import com.pharmaprice.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code docs/ROADMAP.md} T-32 / {@code docs/API.md} §8 제보 관리 2개 엔드포인트.
 *
 * <p>{@code AdminStatsController}와 동일하게 {@code SecurityConfig}의 URL 패턴
 * 매칭과 별개로 메서드 레벨 {@code @PreAuthorize}를 이중으로 건다.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/price-reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AdminReportListItemResponse> list(
        @RequestParam(required = false) Boolean flagged,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long pharmacyId,
        @RequestParam(required = false) Long drugId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return adminReportService.list(flagged, status, pharmacyId, drugId, page, size);
    }

    @PatchMapping("/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminReportUpdateResponse update(@PathVariable Long reportId,
                                             @RequestBody AdminReportUpdateRequest request) {
        return adminReportService.update(reportId, request);
    }
}
