package com.pharmaprice.report.repository;

import com.pharmaprice.report.domain.PriceReport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@code docs/ROADMAP.md} T-28 / {@code docs/API.md} §6 제보 목록 조회 전용 native
 * 쿼리 리포지토리. {@link com.pharmaprice.pharmacy.repository.PharmacyQueryRepository}
 * ·{@link com.pharmaprice.drug.repository.DrugQueryRepository}와 동일하게
 * LIMIT/OFFSET + 별도 count 쿼리 패턴을 따른다(프로젝트 전체가 Spring Data
 * {@code Pageable}을 쓰지 않는 컨벤션이라 여기서도 그대로 따른다).
 *
 * <p>{@code status = 'HIDDEN'}인 제보는 항상 제외한다 - {@code PharmacyServiceImpl.getPriceHistory}
 * ({@code PriceReportRepository}의 {@code StatusNot} 파생 쿼리)가 이미 같은 기준(HIDDEN만
 * 제외, REJECTED는 노출)을 쓰고 있어 일관성을 맞췄다. {@code reporterNickname}은
 * {@code user_id IS NULL}인 시드 데이터 제보에서 LEFT JOIN으로 인해 {@code null}이 된다.</p>
 *
 * <p>{@code search}/{@code count} 둘 다 WHERE 절이 반드시 동일해야 한다.</p>
 */
public interface PriceReportQueryRepository extends Repository<PriceReport, Long> {

    @Query(value = """
        SELECT r.id AS id,
               p.id AS pharmacy_id, p.name AS pharmacy_name,
               d.id AS drug_id, d.display_name AS drug_display_name, d.package_unit AS drug_package_unit,
               r.price AS price, r.purchased_at AS purchased_at,
               u.nickname AS reporter_nickname,
               r.source AS source, r.status AS status, r.flagged AS flagged,
               (r.receipt_file_id IS NOT NULL) AS has_receipt,
               r.created_at AS created_at
        FROM price_report r
        JOIN pharmacy p ON p.id = r.pharmacy_id
        JOIN drug d ON d.id = r.drug_id
        LEFT JOIN app_user u ON u.id = r.user_id
        WHERE r.status <> 'HIDDEN'
          AND (:pharmacyId IS NULL OR r.pharmacy_id = :pharmacyId)
          AND (:drugId IS NULL OR r.drug_id = :drugId)
          AND (:userId IS NULL OR r.user_id = :userId)
        ORDER BY r.created_at DESC, r.id DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<PriceReportRow> search(@Param("pharmacyId") Long pharmacyId, @Param("drugId") Long drugId,
                                 @Param("userId") Long userId, @Param("limit") int limit, @Param("offset") long offset);

    /** {@link #search}와 동일한 WHERE 절의 총 건수. */
    @Query(value = """
        SELECT COUNT(*)
        FROM price_report r
        WHERE r.status <> 'HIDDEN'
          AND (:pharmacyId IS NULL OR r.pharmacy_id = :pharmacyId)
          AND (:drugId IS NULL OR r.drug_id = :drugId)
          AND (:userId IS NULL OR r.user_id = :userId)
        """, nativeQuery = true)
    long count(@Param("pharmacyId") Long pharmacyId, @Param("drugId") Long drugId, @Param("userId") Long userId);

    /**
     * {@link #search} 결과 프로젝션. {@code reporterNickname}은 시드 데이터(user_id NULL)면
     * {@code null}. {@code source}/{@code status}는 enum이 아니라 원문 문자열로 받아 서비스
     * 계층에서 {@code valueOf}로 변환한다 - 다른 native 프로젝션 어디에도 enum 컬럼을 직접
     * 받는 선례가 없어 안전한 쪽(String)을 택했다.
     *
     * <p>{@code createdAt}은 {@code OffsetDateTime}이 아니라 {@code Instant}로 받는다 -
     * pgjdbc가 native 프로젝션에는 {@code timestamptz}를 {@code Instant}로 돌려주고,
     * Spring Data의 프로젝션 프록시는 {@code Instant → OffsetDateTime}을 자동 변환하지
     * 못해 {@code UnsupportedOperationException}이 난다(실측). 서비스 계층에서
     * {@code atZone(ZoneId.systemDefault())}로 변환한다.</p>
     */
    interface PriceReportRow {
        Long getId();
        Long getPharmacyId();
        String getPharmacyName();
        Long getDrugId();
        String getDrugDisplayName();
        String getDrugPackageUnit();
        int getPrice();
        LocalDate getPurchasedAt();
        String getReporterNickname();
        String getSource();
        String getStatus();
        boolean isFlagged();
        boolean isHasReceipt();
        Instant getCreatedAt();
    }
}
