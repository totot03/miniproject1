package com.pharmaprice.report.repository;

import com.pharmaprice.report.domain.PriceReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceReportRepository extends JpaRepository<PriceReport, Long> {

    /** T-30 "내 제보 목록"에서 본인 제보인지 확인할 때 쓴다. */
    Optional<PriceReport> findByIdAndUserId(Long id, Long userId);
}
