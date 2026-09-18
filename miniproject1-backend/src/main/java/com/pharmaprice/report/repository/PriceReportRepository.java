package com.pharmaprice.report.repository;

import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceReportRepository extends JpaRepository<PriceReport, Long> {

    /** T-30 "내 제보 목록"에서 본인 제보인지 확인할 때 쓴다. */
    Optional<PriceReport> findByIdAndUserId(Long id, Long userId);

    /** T-31 관리자 통계 overview의 flaggedReportCount. */
    long countByFlaggedTrue();

    /**
     * T-20 가격 이력 API 전용 조회. {@code PriceReport}는 스칼라 컬럼(price/purchasedAt/flagged)만
     * 필요하고 jsonb 컬럼이나 다중 테이블 집계가 없어 {@code PharmacyQueryRepository}식
     * 네이티브 쿼리+프로젝션이 필요 없다 — {@code PriceStatRepository.findByPharmacyIdAndDrugId}와
     * 동일하게 연관관계 프로퍼티 탐색({@code pharmacy.id}, {@code drug.id})을 쓰는 Spring Data
     * derived query로 충분하다.
     *
     * <p>{@code excludedStatus}에는 항상 {@link ReportStatus#HIDDEN}을 넘긴다 — API.md §4
     * history 스펙상 숨김 처리된 제보만 제외하고 {@code REJECTED}/{@code ACTIVE}는 그대로
     * 포함해야 하기 때문이다.</p>
     */
    List<PriceReport> findByPharmacy_IdAndDrug_IdAndStatusNotAndPurchasedAtGreaterThanEqualOrderByPurchasedAtAsc(
        Long pharmacyId, Long drugId, ReportStatus excludedStatus, LocalDate fromDate);

    /**
     * T-26 이상치 판정 전용. {@code PriceStatRepository.aggregate}와 달리 특정 약국이나
     * 날짜 윈도우로 좁히지 않고, 그 약품의 전 약국·전 기간 유효 제보(ACTIVE, 미플래그)
     * 만으로 중앙값을 구한다({@code docs/PRD.md} F2-9). 대상이 0건이면
     * {@code percentile_cont}가 SQL {@code NULL}을 반환하므로 그대로 {@code null}이
     * 되고, 이 경우 호출부는 비교할 기준이 없다고 보고 이상치 판정 자체를 건너뛴다.
     */
    @Query(value = """
        SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY price)::int
        FROM price_report
        WHERE drug_id = :drugId AND status = 'ACTIVE' AND flagged = false
        """, nativeQuery = true)
    Integer findDrugWideMedianPrice(@Param("drugId") Long drugId);
}
