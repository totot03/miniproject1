package com.pharmaprice.admin.dto;

import com.pharmaprice.report.domain.ReportStatus;

/**
 * {@code docs/API.md} §8 {@code PATCH /admin/price-reports/{reportId}} 요청.
 *
 * <p>세 필드 모두 선택이며, 넘긴 필드만 변경한다. {@code reason}은 관리자가
 * 조치 사유를 남기는 자유 텍스트지만, {@code docs/DATABASE.md} §3.5
 * {@code price_report} 테이블에 이를 저장할 컬럼이 없고 응답 스펙에도 없다 -
 * 이번 태스크 범위에서는 새 마이그레이션을 추가하지 않고, 요청 파싱만
 * 지원하며 영속화하지 않는다.</p>
 */
public record AdminReportUpdateRequest(
    ReportStatus status,
    Boolean flagged,
    String reason
) {
}
