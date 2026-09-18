package com.pharmaprice.admin.dto;

import com.pharmaprice.report.domain.ReportStatus;
import java.time.OffsetDateTime;

/**
 * {@code docs/API.md} §8 {@code PATCH /admin/price-reports/{reportId}} 응답.
 *
 * <p>{@code recalculatedStat}은 상태/플래그 변경 직후 동기 재계산된 결과다.
 * 유효 제보가 0건이 되어 {@code PriceStatService.recalculate}가 통계 행을
 * 삭제한 경우 {@code null}이다.</p>
 */
public record AdminReportUpdateResponse(
    Long id,
    ReportStatus status,
    boolean flagged,
    OffsetDateTime updatedAt,
    RecalculatedStat recalculatedStat
) {

    public record RecalculatedStat(Long pharmacyId, Long drugId, int repPrice, int reportCount) {
    }
}
