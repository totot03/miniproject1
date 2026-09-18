package com.pharmaprice.report.service;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.report.dto.PriceReportCreateRequest;
import com.pharmaprice.report.dto.PriceReportListItemResponse;
import com.pharmaprice.report.dto.PriceReportResponse;

/**
 * {@code docs/ROADMAP.md} T-26/T-28. 가격 제보 생성(검증 → 저장 → 통계 재계산 →
 * report_count 증가)과 목록 조회를 담당한다.
 */
public interface PriceReportService {

    /**
     * @param userId  JWT에서 복원한 인증 사용자 id
     * @param request 제보 요청
     * @return 생성된 제보와, 재계산 후 통계(유효 제보가 하나도 없으면 {@code null})
     */
    PriceReportResponse create(Long userId, PriceReportCreateRequest request);

    /**
     * @param pharmacyId 필터. {@code null}이면 전체
     * @param drugId     필터. {@code null}이면 전체
     * @param userId     {@code mine=true}일 때 인증 사용자 id, 아니면 {@code null}(컨트롤러가 분기)
     */
    PageResponse<PriceReportListItemResponse> list(Long pharmacyId, Long drugId, Long userId, int page, int size);
}
