package com.pharmaprice.report.service;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.service.PriceStatService;
import com.pharmaprice.report.domain.FlagReason;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportSource;
import com.pharmaprice.report.domain.UploadedFile;
import com.pharmaprice.report.dto.PriceReportCreateRequest;
import com.pharmaprice.report.dto.PriceReportResponse;
import com.pharmaprice.report.dto.PriceReportResponse.UpdatedStat;
import com.pharmaprice.report.exception.DrugNotFoundException;
import com.pharmaprice.report.exception.DrugNotOtcException;
import com.pharmaprice.report.exception.InvalidDateRangeException;
import com.pharmaprice.report.exception.PharmacyNotFoundException;
import com.pharmaprice.report.repository.PriceReportRepository;
import com.pharmaprice.report.repository.UploadedFileRepository;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code docs/ROADMAP.md} T-26 구현체.
 *
 * <p>이상치 판정 기준 중앙값은 {@code PriceStatRepository.aggregate}(약국+약품 쌍,
 * 90/180일 윈도우 한정)와 범위가 달라 {@code PriceReportRepository.findDrugWideMedianPrice}
 * (약품 전체, 기간 제한 없음)를 쓴다({@code docs/PRD.md} F2-9). {@code priceStatService.recalculate}는
 * 이상치 여부와 무관하게 항상 호출한다 - 그 내부 집계 쿼리가 이미 {@code flagged = false}만
 * 계산하므로 방금 저장한 이상치 제보는 자동으로 통계에서 제외된다.</p>
 */
@Service
@RequiredArgsConstructor
public class PriceReportServiceImpl implements PriceReportService {

    /** {@code docs/PRD.md} F2-9. */
    private static final double OUTLIER_LOW_RATIO = 0.3;
    private static final double OUTLIER_HIGH_RATIO = 3.0;

    /** {@code docs/API.md} §6 "미래 불가, 180일 초과 과거 불가". */
    private static final int MAX_PAST_DAYS = 180;

    private final PharmacyRepository pharmacyRepository;
    private final DrugRepository drugRepository;
    private final AppUserRepository appUserRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final PriceReportRepository priceReportRepository;
    private final PriceStatService priceStatService;

    @Override
    @Transactional
    public PriceReportResponse create(Long userId, PriceReportCreateRequest request) {
        Pharmacy pharmacy = pharmacyRepository.findById(request.pharmacyId())
            .orElseThrow(() -> new PharmacyNotFoundException("pharmacy not found: " + request.pharmacyId()));
        Drug drug = drugRepository.findById(request.drugId())
            .orElseThrow(() -> new DrugNotFoundException("drug not found: " + request.drugId()));
        if (!drug.isOtcFlag()) {
            throw new DrugNotOtcException("전문의약품은 제보할 수 없습니다: " + request.drugId());
        }

        LocalDate purchasedAt = resolvePurchasedAt(request.purchasedAt());
        Outlier outlier = detectOutlier(request.drugId(), request.price());

        // T-27(업로드 API)이 아직 없어 존재하지 않는 receiptFileId의 에러 코드가 정의돼 있지 않다 -
        // 사용자 확정에 따라 조용히 무시한다(찾지 못하면 null).
        UploadedFile receiptFile = request.receiptFileId() != null
            ? uploadedFileRepository.findById(request.receiptFileId()).orElse(null)
            : null;

        // JWT로 인증된 본인 id 조회 - AuthServiceImpl.me()와 동일하게 신뢰하는 bare orElseThrow.
        AppUser user = appUserRepository.findById(userId).orElseThrow();

        PriceReport report = PriceReport.builder()
            .pharmacy(pharmacy)
            .drug(drug)
            .user(user)
            .price(request.price())
            .purchasedAt(purchasedAt)
            .source(ReportSource.FORM)
            .flagged(outlier.flagged())
            .flagReason(outlier.flagReason())
            .receiptFile(receiptFile)
            .memo(request.memo())
            .build();
        // uq_report_user_pair_day 위반 시 DataIntegrityViolationException이 그대로 전파된다 -
        // PriceReportExceptionHandler가 409 DUPLICATE_REPORT로 변환한다.
        report = priceReportRepository.save(report);

        user.increaseReportCount();

        Optional<PharmacyDrugPriceStat> stat = priceStatService.recalculate(request.pharmacyId(), request.drugId());

        return toResponse(report, outlier.warning(), stat.orElse(null));
    }

    private LocalDate resolvePurchasedAt(LocalDate purchasedAt) {
        LocalDate today = LocalDate.now();
        LocalDate resolved = purchasedAt != null ? purchasedAt : today;
        if (resolved.isAfter(today) || resolved.isBefore(today.minusDays(MAX_PAST_DAYS))) {
            throw new InvalidDateRangeException("purchasedAt은 오늘 이전이면서 180일 이내여야 합니다: " + resolved);
        }
        return resolved;
    }

    /** 비교할 기준(전체 유효 제보)이 하나도 없으면 판정 자체를 건너뛴다. */
    private Outlier detectOutlier(Long drugId, int price) {
        Integer median = priceReportRepository.findDrugWideMedianPrice(drugId);
        if (median == null) {
            return new Outlier(false, null, null);
        }

        double lowBound = median * OUTLIER_LOW_RATIO;
        double highBound = median * OUTLIER_HIGH_RATIO;
        if (price >= lowBound && price <= highBound) {
            return new Outlier(false, null, null);
        }

        FlagReason reason = price < lowBound ? FlagReason.OUTLIER_LOW : FlagReason.OUTLIER_HIGH;
        String warning = "입력하신 가격이 이 약품의 일반적인 가격대(%,d~%,d원)와 크게 달라 통계에 반영되지 않았습니다. 관리자 확인 후 반영됩니다."
            .formatted(Math.round(lowBound), Math.round(highBound));
        return new Outlier(true, reason, warning);
    }

    private PriceReportResponse toResponse(PriceReport report, String warning, PharmacyDrugPriceStat stat) {
        UpdatedStat updatedStat = stat == null ? null : new UpdatedStat(
            stat.getRepPrice(), stat.getMinPrice(), stat.getMaxPrice(), stat.getAvgPrice(),
            stat.getReportCount(), stat.getLastReportedAt());
        return new PriceReportResponse(
            report.getId(), report.getPharmacy().getId(), report.getDrug().getId(), report.getPrice(),
            report.getPurchasedAt(), report.getStatus(), report.isFlagged(), report.getFlagReason(),
            report.getCreatedAt(), warning, updatedStat);
    }

    private record Outlier(boolean flagged, FlagReason flagReason, String warning) {
    }
}
