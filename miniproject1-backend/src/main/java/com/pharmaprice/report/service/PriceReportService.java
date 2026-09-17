package com.pharmaprice.report.service;

import com.pharmaprice.report.dto.PriceReportCreateRequest;
import com.pharmaprice.report.dto.PriceReportResponse;

/**
 * {@code docs/ROADMAP.md} T-26. 가격 제보 생성(검증 → 저장 → 통계 재계산 →
 * report_count 증가)을 하나의 트랜잭션으로 묶는다.
 */
public interface PriceReportService {

    /**
     * @param userId  JWT에서 복원한 인증 사용자 id
     * @param request 제보 요청
     * @return 생성된 제보와, 재계산 후 통계(유효 제보가 하나도 없으면 {@code null})
     */
    PriceReportResponse create(Long userId, PriceReportCreateRequest request);
}
