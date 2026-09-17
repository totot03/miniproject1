package com.pharmaprice.report.dto;

import com.pharmaprice.report.domain.FlagReason;
import com.pharmaprice.report.domain.ReportStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code docs/API.md} §6 {@code POST /price-reports} 응답.
 *
 * <p>{@code warning}은 이상치로 플래그된 경우에만 채워지고, 그 외에는 {@code null}이다.
 * {@code updatedStat}도 해당 (약국, 약품) 쌍에 유효 제보가 하나도 없어
 * {@code PriceStatService.recalculate}가 통계 행을 삭제한 경우 {@code null}일 수 있다.</p>
 */
public record PriceReportResponse(
    Long id,
    Long pharmacyId,
    Long drugId,
    int price,
    LocalDate purchasedAt,
    ReportStatus status,
    boolean flagged,
    FlagReason flagReason,
    OffsetDateTime createdAt,
    String warning,
    UpdatedStat updatedStat
) {

    /** {@code PharmacyDrugPriceStat} 필드와 1:1 대응하는 재계산 결과 스냅샷. */
    public record UpdatedStat(
        int repPrice,
        int minPrice,
        int maxPrice,
        int avgPrice,
        int reportCount,
        LocalDate lastReportedAt
    ) {
    }
}
