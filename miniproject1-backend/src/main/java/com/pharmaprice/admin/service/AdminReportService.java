package com.pharmaprice.admin.service;

import com.pharmaprice.admin.dto.AdminReportListItemResponse;
import com.pharmaprice.admin.dto.AdminReportUpdateRequest;
import com.pharmaprice.admin.dto.AdminReportUpdateResponse;
import com.pharmaprice.common.dto.PageResponse;

/**
 * {@code docs/ROADMAP.md} T-32 / {@code docs/API.md} §8 관리자 제보 관리 2개 엔드포인트.
 */
public interface AdminReportService {

    /** {@code flagged}/{@code status}/{@code pharmacyId}/{@code drugId} 전부 선택 필터. */
    PageResponse<AdminReportListItemResponse> list(Boolean flagged, String status, Long pharmacyId, Long drugId,
                                                     int page, int size);

    /**
     * {@code status}/{@code flagged}/{@code reason} 중 넘어온 필드만 변경하고,
     * 해당 (약국, 약품) 통계를 동기 재계산해 결과를 응답에 담는다.
     */
    AdminReportUpdateResponse update(Long reportId, AdminReportUpdateRequest request);
}
