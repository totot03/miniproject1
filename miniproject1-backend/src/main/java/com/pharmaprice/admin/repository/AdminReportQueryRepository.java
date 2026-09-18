package com.pharmaprice.admin.repository;

import com.pharmaprice.report.domain.PriceReport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@code docs/ROADMAP.md} T-32 / {@code docs/API.md} §8 {@code GET /admin/price-reports}
 * 전용 native 쿼리 리포지토리.
 *
 * <p>{@code report.repository.PriceReportQueryRepository}와 목적은 비슷하지만 그대로
 * 재사용할 수 없다 - 공개 목록은 {@code status <> 'HIDDEN'}을 항상 강제하지만, 관리자는
 * 숨긴 제보도 관리 대상이라 이 조건을 걸면 안 된다. 대신 {@code flagged}/{@code status}를
 * 옵션 필터로 받는다. SELECT 컬럼도 다르다 - 공개 목록은 개인정보 보호를 위해
 * {@code reporter.nickname}만 노출하지만, 관리자 화면은 {@code reporter.id}/{@code email},
 * {@code flagReason}, {@code receiptFileId}(실제 파일 id, 공개 목록의 {@code hasReceipt}
 * boolean과 다름)까지 내려줘야 한다({@code docs/API.md} §8).</p>
 *
 * <p>{@code status}는 enum이 아니라 원문 문자열로 받아 서비스 계층에서 {@code valueOf}로
 * 변환한다 - {@code PriceReportQueryRepository.PriceReportRow}와 동일한 선택.
 * {@code createdAt}도 같은 이유로 {@code Instant}로 받는다({@code docs/DATABASE.md}
 * 관련 메모: native 프로젝션에서 pgjdbc가 {@code timestamptz}를 {@code Instant}로
 * 돌려주고, Spring Data 프로젝션 프록시가 {@code Instant → OffsetDateTime} 자동 변환을
 * 못 해 {@code UnsupportedOperationException}이 난다).</p>
 *
 * <p>{@code search}/{@code count} 둘 다 WHERE 절이 반드시 동일해야 한다.</p>
 */
public interface AdminReportQueryRepository extends Repository<PriceReport, Long> {

    @Query(value = """
        SELECT r.id AS id,
               p.id AS pharmacy_id, p.name AS pharmacy_name,
               d.id AS drug_id, d.display_name AS drug_display_name, d.package_unit AS drug_package_unit,
               r.price AS price, r.purchased_at AS purchased_at,
               u.id AS reporter_id, u.email AS reporter_email,
               r.source AS source, r.status AS status, r.flagged AS flagged,
               r.flag_reason AS flag_reason, r.receipt_file_id AS receipt_file_id,
               r.created_at AS created_at
        FROM price_report r
        JOIN pharmacy p ON p.id = r.pharmacy_id
        JOIN drug d ON d.id = r.drug_id
        LEFT JOIN app_user u ON u.id = r.user_id
        WHERE (:flagged IS NULL OR r.flagged = :flagged)
          AND (:status IS NULL OR r.status = :status)
          AND (:pharmacyId IS NULL OR r.pharmacy_id = :pharmacyId)
          AND (:drugId IS NULL OR r.drug_id = :drugId)
        ORDER BY r.created_at DESC, r.id DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<AdminReportRow> search(@Param("flagged") Boolean flagged, @Param("status") String status,
                                 @Param("pharmacyId") Long pharmacyId, @Param("drugId") Long drugId,
                                 @Param("limit") int limit, @Param("offset") long offset);

    /** {@link #search}와 동일한 WHERE 절의 총 건수. */
    @Query(value = """
        SELECT COUNT(*)
        FROM price_report r
        WHERE (:flagged IS NULL OR r.flagged = :flagged)
          AND (:status IS NULL OR r.status = :status)
          AND (:pharmacyId IS NULL OR r.pharmacy_id = :pharmacyId)
          AND (:drugId IS NULL OR r.drug_id = :drugId)
        """, nativeQuery = true)
    long count(@Param("flagged") Boolean flagged, @Param("status") String status,
               @Param("pharmacyId") Long pharmacyId, @Param("drugId") Long drugId);

    /**
     * {@link #search} 결과 프로젝션. {@code reporterId}/{@code reporterEmail}은 시드 데이터
     * (user_id NULL)면 둘 다 {@code null}. {@code flagReason}/{@code receiptFileId}도
     * 선택 컬럼이라 {@code null}일 수 있다.
     */
    interface AdminReportRow {
        Long getId();
        Long getPharmacyId();
        String getPharmacyName();
        Long getDrugId();
        String getDrugDisplayName();
        String getDrugPackageUnit();
        int getPrice();
        LocalDate getPurchasedAt();
        Long getReporterId();
        String getReporterEmail();
        String getSource();
        String getStatus();
        boolean isFlagged();
        String getFlagReason();
        Long getReceiptFileId();
        Instant getCreatedAt();
    }
}
