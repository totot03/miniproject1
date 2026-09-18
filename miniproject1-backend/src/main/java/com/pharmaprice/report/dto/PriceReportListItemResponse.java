package com.pharmaprice.report.dto;

import com.pharmaprice.report.domain.ReportSource;
import com.pharmaprice.report.domain.ReportStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code docs/API.md} §6 {@code GET /price-reports} 목록 항목.
 *
 * <p>{@code reporter}는 닉네임만 노출한다 - id·이메일을 내려주면 개인정보 노출이다
 * ({@code docs/ROADMAP.md} T-28). {@code user_id}가 없는 시드 데이터 제보는
 * {@code reporter}가 {@code null}이다. {@code hasReceipt}도 같은 이유로 파일 id 대신
 * boolean으로만 노출한다({@code receiptFile != null}).</p>
 */
public record PriceReportListItemResponse(
    Long id,
    Pharmacy pharmacy,
    Drug drug,
    int price,
    LocalDate purchasedAt,
    Reporter reporter,
    ReportSource source,
    ReportStatus status,
    boolean flagged,
    boolean hasReceipt,
    OffsetDateTime createdAt
) {

    public record Pharmacy(Long id, String name) {
    }

    public record Drug(Long id, String displayName, String packageUnit) {
    }

    public record Reporter(String nickname) {
    }
}
