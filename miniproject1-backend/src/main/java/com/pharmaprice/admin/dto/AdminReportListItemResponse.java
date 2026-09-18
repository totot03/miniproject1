package com.pharmaprice.admin.dto;

import com.pharmaprice.report.domain.FlagReason;
import com.pharmaprice.report.domain.ReportSource;
import com.pharmaprice.report.domain.ReportStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code docs/API.md} §8 {@code GET /admin/price-reports} 목록 항목.
 *
 * <p>{@code report.dto.PriceReportListItemResponse}(공개 목록)와 달리 개인정보
 * 노출 제약이 없어 {@code reporter}에 id·email을 그대로 담고, {@code flagReason}과
 * {@code receiptFileId}(실제 파일 id - 관리자가 {@code GET /uploads/{fileId}}로
 * 열람하기 위함)를 추가로 노출한다. {@code user_id}가 없는 시드 데이터 제보는
 * {@code reporter}가 {@code null}이다.</p>
 */
public record AdminReportListItemResponse(
    Long id,
    Pharmacy pharmacy,
    Drug drug,
    int price,
    LocalDate purchasedAt,
    Reporter reporter,
    ReportSource source,
    ReportStatus status,
    boolean flagged,
    FlagReason flagReason,
    Long receiptFileId,
    OffsetDateTime createdAt
) {

    public record Pharmacy(Long id, String name) {
    }

    public record Drug(Long id, String displayName, String packageUnit) {
    }

    public record Reporter(Long id, String email) {
    }
}
