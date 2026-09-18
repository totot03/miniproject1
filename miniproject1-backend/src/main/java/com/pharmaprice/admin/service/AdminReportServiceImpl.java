package com.pharmaprice.admin.service;

import com.pharmaprice.admin.dto.AdminReportListItemResponse;
import com.pharmaprice.admin.dto.AdminReportUpdateRequest;
import com.pharmaprice.admin.dto.AdminReportUpdateResponse;
import com.pharmaprice.admin.dto.AdminReportUpdateResponse.RecalculatedStat;
import com.pharmaprice.admin.exception.ReportNotFoundException;
import com.pharmaprice.admin.repository.AdminReportQueryRepository;
import com.pharmaprice.admin.repository.AdminReportQueryRepository.AdminReportRow;
import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.service.PriceStatService;
import com.pharmaprice.report.domain.FlagReason;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportSource;
import com.pharmaprice.report.domain.ReportStatus;
import com.pharmaprice.report.repository.PriceReportRepository;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AdminReportService} 구현. {@code report.service.PriceReportServiceImpl}과
 * 동일하게 목록은 {@link AdminReportQueryRepository}의 native 쿼리 결과를
 * {@link PageResponse}로 조립하고, 상태 변경은 {@link PriceReport}의 기존 도메인
 * 메서드(hide/restore/reject/flag/unflag)를 그대로 호출한다.
 *
 * <p>{@code update()}에서 상태/플래그를 변경한 뒤 {@code priceReportRepository.saveAndFlush}로
 * 명시적으로 flush한다 - {@link PriceStatService#recalculate}가 내부적으로 실행하는
 * {@code PriceStatRepository.aggregate}는 {@code @Modifying}이 없는 순수 native SELECT라
 * Hibernate가 실행 전 자동으로 dirty 엔티티를 flush해주지 않는다(JPQL과 달리 native
 * 쿼리는 영속성 컨텍스트와 무관하게 취급됨). flush 없이 recalculate를 호출하면 방금
 * 바꾼 status/flagged가 아직 DB에 반영되지 않아 재계산 결과가 예전 상태 기준으로
 * 나오는 버그가 생긴다 - ROADMAP T-32 완료 판정("HIDDEN으로 바꾸면 rep_price가
 * 즉시 재계산된다")을 정면으로 어기므로 반드시 필요한 flush다.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminReportServiceImpl implements AdminReportService {

    /** {@code report.service.PriceReportServiceImpl.MAX_PAGE_SIZE}와 동일한 clamp 관례. */
    private static final int MAX_PAGE_SIZE = 50;

    private final PriceReportRepository priceReportRepository;
    private final AdminReportQueryRepository adminReportQueryRepository;
    private final PriceStatService priceStatService;

    @Override
    public PageResponse<AdminReportListItemResponse> list(Boolean flagged, String status, Long pharmacyId,
                                                            Long drugId, int page, int size) {
        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long offset = (long) safePage * clampedSize;

        List<AdminReportRow> rows = adminReportQueryRepository.search(flagged, status, pharmacyId, drugId,
            clampedSize, offset);
        long totalElements = adminReportQueryRepository.count(flagged, status, pharmacyId, drugId);

        List<AdminReportListItemResponse> content = rows.stream().map(this::toListItem).toList();
        return PageResponse.of(content, safePage, clampedSize, totalElements);
    }

    @Override
    @Transactional
    public AdminReportUpdateResponse update(Long reportId, AdminReportUpdateRequest request) {
        PriceReport report = priceReportRepository.findById(reportId)
            .orElseThrow(() -> new ReportNotFoundException("report not found: " + reportId));

        if (request.status() != null) {
            applyStatus(report, request.status());
        }
        if (request.flagged() != null) {
            if (request.flagged()) {
                report.flag(FlagReason.MANUAL);
            } else {
                report.unflag();
            }
        }

        // recalculate()가 실행할 native 쿼리가 방금 바꾼 status/flagged를 읽을 수 있도록
        // 명시적으로 flush한다(위 클래스 javadoc 참고). saveAndFlush는 영속성 컨텍스트
        // 전체를 flush하므로 REJECTED 전환 시 함께 변경된 report.getUser()의
        // report_count도 이 시점에 같이 반영된다.
        report = priceReportRepository.saveAndFlush(report);

        Optional<PharmacyDrugPriceStat> stat = priceStatService.recalculate(
            report.getPharmacy().getId(), report.getDrug().getId());

        RecalculatedStat recalculatedStat = stat.map(s -> new RecalculatedStat(
            s.getPharmacy().getId(), s.getDrug().getId(), s.getRepPrice(), s.getReportCount())).orElse(null);

        return new AdminReportUpdateResponse(report.getId(), report.getStatus(), report.isFlagged(),
            report.getUpdatedAt(), recalculatedStat);
    }

    private void applyStatus(PriceReport report, ReportStatus status) {
        switch (status) {
            case HIDDEN -> report.hide();
            case ACTIVE -> report.restore();
            case REJECTED -> {
                boolean wasRejected = report.getStatus() == ReportStatus.REJECTED;
                report.reject();
                if (!wasRejected && report.getUser() != null) {
                    report.getUser().decreaseReportCount();
                }
            }
        }
    }

    private AdminReportListItemResponse toListItem(AdminReportRow row) {
        AdminReportListItemResponse.Reporter reporter = row.getReporterId() == null
            ? null
            : new AdminReportListItemResponse.Reporter(row.getReporterId(), row.getReporterEmail());
        FlagReason flagReason = row.getFlagReason() == null ? null : FlagReason.valueOf(row.getFlagReason());
        return new AdminReportListItemResponse(
            row.getId(),
            new AdminReportListItemResponse.Pharmacy(row.getPharmacyId(), row.getPharmacyName()),
            new AdminReportListItemResponse.Drug(row.getDrugId(), row.getDrugDisplayName(), row.getDrugPackageUnit()),
            row.getPrice(), row.getPurchasedAt(), reporter,
            ReportSource.valueOf(row.getSource()), ReportStatus.valueOf(row.getStatus()),
            row.isFlagged(), flagReason, row.getReceiptFileId(),
            row.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }
}
