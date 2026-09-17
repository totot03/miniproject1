package com.pharmaprice.report.repository;

import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceReportRepository extends JpaRepository<PriceReport, Long> {

    /** T-30 "내 제보 목록"에서 본인 제보인지 확인할 때 쓴다. */
    Optional<PriceReport> findByIdAndUserId(Long id, Long userId);

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
}
