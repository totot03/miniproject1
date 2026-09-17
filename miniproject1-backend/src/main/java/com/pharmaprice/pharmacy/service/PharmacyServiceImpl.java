package com.pharmaprice.pharmacy.service;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse.DrugPriceItem;
import com.pharmaprice.pharmacy.dto.PharmacySummaryResponse;
import com.pharmaprice.pharmacy.dto.PriceHistoryResponse;
import com.pharmaprice.pharmacy.dto.PriceHistoryResponse.PricePoint;
import com.pharmaprice.pharmacy.dto.RegionInfo;
import com.pharmaprice.pharmacy.repository.PharmacyQueryRepository;
import com.pharmaprice.pharmacy.repository.PharmacyQueryRepository.DrugPriceRow;
import com.pharmaprice.pharmacy.repository.PharmacyQueryRepository.PharmacyRow;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.recommendation.distance.DistanceCalculator;
import com.pharmaprice.recommendation.distance.DistanceCalculator.BoundingBox;
import com.pharmaprice.report.domain.ReportStatus;
import com.pharmaprice.report.repository.PriceReportRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code docs/ROADMAP.md} T-19 / {@code docs/API.md} §4 구현체.
 *
 * <p>목록의 좌표 검색은 T-15 {@code SearchServiceImpl.findCandidateContexts}와
 * 동일한 2단계(바운딩박스 SQL 프리필터 → Java 정확 거리 필터링)를 쓰지만,
 * 이 API는 {@code page}/{@code size} 페이지네이션이 필수라서 SQL에 LIMIT/OFFSET을
 * 걸지 않고 필터링된 전체 리스트 크기로 {@code totalElements}를 계산한 뒤
 * Java에서 서브리스트를 잘라낸다 — 그래야 반경 밖으로 걸러진 건수가
 * 페이지네이션 총계와 어긋나지 않는다.</p>
 *
 * <p><b>T-20 {@link PriceReportRepository} 의존</b>: {@code report} 패키지가 이미
 * {@code PriceReport} 엔티티로 {@code pharmacy}/{@code drug} 패키지를 참조하고 있어,
 * 이 서비스가 {@code PriceReportRepository}를 쓰면 반대 방향(pharmacy → report) 참조가
 * 하나 더 생긴다. 순환은 아니지만(엔티티 참조와 리포지토리 사용 방향이 반대) 가격 이력이
 * 약국 상세 화면(T-21)의 일부라 여기 두는 편이 자연스럽다고 판단했다.</p>
 */
@Service
@RequiredArgsConstructor
public class PharmacyServiceImpl implements PharmacyService {

    /** docs/API.md §4 "size" 상한 명시는 없지만 DrugServiceImpl.MAX_PAGE_SIZE 선례를 따른다. */
    private static final int MAX_PAGE_SIZE = 50;

    /** docs/API.md §4 radius 기본값. */
    private static final int DEFAULT_RADIUS_M = 2000;

    /** docs/API.md §4 radius "최대 10000" — 초과 입력은 에러 대신 조용히 상한으로 clamp한다(size와 동일한 방어적 습관). */
    private static final int MAX_RADIUS_M = 10_000;

    /** docs/ROADMAP.md T-20 "days 기본 180". */
    private static final int DEFAULT_HISTORY_DAYS = 180;

    /** docs/ROADMAP.md T-20 "days ... 최대 365" — radius/size와 동일하게 초과 입력은 조용히 clamp한다. */
    private static final int MAX_HISTORY_DAYS = 365;

    private final PharmacyQueryRepository pharmacyQueryRepository;
    private final PharmacyRepository pharmacyRepository;
    private final DistanceCalculator distanceCalculator;
    private final PriceReportRepository priceReportRepository;

    @Override
    public PageResponse<PharmacySummaryResponse> list(String q, Double lat, Double lng, Integer radius,
                                                        int page, int size) {
        String qParam = (q != null && !q.isBlank()) ? q : null;
        boolean hasLocation = lat != null && lng != null;
        if (qParam == null && !hasLocation) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q 또는 lat/lng 중 하나는 필수입니다.");
        }

        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        return hasLocation
            ? listNearby(qParam, lat, lng, radius, safePage, clampedSize)
            : listByQuery(qParam, safePage, clampedSize);
    }

    /** q만 있는 경로 — 위치 기반 2차 필터링이 없어 SQL LIMIT/OFFSET을 그대로 써도 totalElements가 정확하다. */
    private PageResponse<PharmacySummaryResponse> listByQuery(String q, int page, int size) {
        long offset = (long) page * size;
        List<PharmacyRow> rows = pharmacyQueryRepository.searchByQuery(q, size, offset);
        long totalElements = pharmacyQueryRepository.countByQuery(q);

        List<PharmacySummaryResponse> content = rows.stream().map(row -> toSummary(row, null)).toList();
        return PageResponse.of(content, page, size, totalElements);
    }

    /** lat/lng 있는 경로 — 바운딩박스 후보 전체를 가져와 Java에서 정확 거리 필터·정렬·페이지네이션한다. */
    private PageResponse<PharmacySummaryResponse> listNearby(String q, double lat, double lng, Integer radius,
                                                              int page, int size) {
        DistanceCalculator.validateCoordinate(lat, lng); // 범위 밖이면 IllegalArgumentException — 400 변환은 T-35 몫
        int effectiveRadius = Math.min(Math.max(radius != null ? radius : DEFAULT_RADIUS_M, 1), MAX_RADIUS_M);

        BoundingBox box = distanceCalculator.boundingBox(lat, lng, effectiveRadius);
        List<PharmacyRow> candidates = pharmacyQueryRepository.findCandidatesNearby(
            q, box.minLat(), box.maxLat(), box.minLng(), box.maxLng());

        List<PharmacyWithDistance> nearby = candidates.stream()
            .map(row -> new PharmacyWithDistance(row, distanceCalculator.distanceMeters(lat, lng, row.getLat(), row.getLng())))
            .filter(pwd -> pwd.distanceM() <= effectiveRadius)
            .sorted(Comparator.comparingDouble(PharmacyWithDistance::distanceM))
            .toList();

        List<PharmacySummaryResponse> content = nearby.stream()
            .skip((long) page * size)
            .limit(size)
            .map(pwd -> toSummary(pwd.row(), Math.round(pwd.distanceM())))
            .toList();
        return PageResponse.of(content, page, size, nearby.size());
    }

    @Override
    @Transactional(readOnly = true)
    public PharmacyDetailResponse getDetail(Long pharmacyId, Double lat, Double lng) {
        // region(지연 로딩)은 spring.jpa.open-in-view=false라 이 트랜잭션 안에서 읽어야 한다.
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .filter(Pharmacy::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pharmacy not found: " + pharmacyId));

        Long distanceM = null;
        if (lat != null && lng != null) {
            DistanceCalculator.validateCoordinate(lat, lng);
            distanceM = Math.round(distanceCalculator.distanceMeters(lat, lng, pharmacy.getLat(), pharmacy.getLng()));
        }

        List<DrugPriceItem> drugPrices = pharmacyQueryRepository.findDrugPrices(pharmacyId).stream()
            .map(this::toDrugPriceItem)
            .toList();

        return new PharmacyDetailResponse(
            pharmacy.getId(), pharmacy.getName(), pharmacy.getAddressRoad(), pharmacy.getAddressJibun(),
            pharmacy.getLat(), pharmacy.getLng(), pharmacy.getPhone(), pharmacy.getBusinessHours(),
            distanceM, toRegionInfo(pharmacy.getRegion()), drugPrices);
    }

    /**
     * T-20. {@code pharmacyId}/{@code drugId} 존재성은 검증하지 않는다 — API.md §4
     * history 섹션과 ROADMAP T-20 완료판정 어디에도 근거가 없고, 존재하지 않는
     * 조합이면 자연스럽게 빈 목록이 나온다(0건도 200이어야 하므로 결과적으로 맞다).
     *
     * <p>{@link #getDetail}과 달리 {@code @Transactional}이 필요 없다 — {@code region}처럼
     * 지연 로딩된 연관관계를 읽는 게 아니라 {@code PriceReport}의 스칼라 컬럼
     * (price/purchasedAt/flagged)만 매핑하기 때문이다.</p>
     */
    @Override
    public PriceHistoryResponse getPriceHistory(Long pharmacyId, Long drugId, Integer days) {
        int clampedDays = Math.min(Math.max(days != null ? days : DEFAULT_HISTORY_DAYS, 1), MAX_HISTORY_DAYS);
        LocalDate fromDate = LocalDate.now().minusDays(clampedDays); // JVM 전역 TimeZone(Asia/Seoul) 덕분에 별도 ZoneId 불필요

        List<PricePoint> points = priceReportRepository
            .findByPharmacy_IdAndDrug_IdAndStatusNotAndPurchasedAtGreaterThanEqualOrderByPurchasedAtAsc(
                pharmacyId, drugId, ReportStatus.HIDDEN, fromDate)
            .stream()
            .map(r -> new PricePoint(r.getPurchasedAt(), r.getPrice(), r.isFlagged()))
            .toList();

        return new PriceHistoryResponse(pharmacyId, drugId, points);
    }

    private PharmacySummaryResponse toSummary(PharmacyRow row, Long distanceM) {
        return new PharmacySummaryResponse(row.getId(), row.getName(), row.getAddressRoad(),
            row.getLat(), row.getLng(), row.getPhone(), distanceM, toRegionInfo(row));
    }

    private RegionInfo toRegionInfo(PharmacyRow row) {
        return row.getRegionCode() == null ? null : new RegionInfo(row.getRegionCode(), row.getSido(), row.getSigungu());
    }

    private RegionInfo toRegionInfo(Region region) {
        return region == null ? null : new RegionInfo(region.getCode(), region.getSido(), region.getSigungu());
    }

    private DrugPriceItem toDrugPriceItem(DrugPriceRow row) {
        Integer nationalAvg = row.getNationalAvg();
        Integer diff = nationalAvg == null ? null : row.getRepPrice() - nationalAvg;
        return new DrugPriceItem(row.getDrugId(), row.getDisplayName(), row.getPackageUnit(),
            row.getRepPrice(), row.getMinPrice(), row.getMaxPrice(), row.getAvgPrice(),
            row.getReportCount(), row.getLastReportedAt(), nationalAvg, diff);
    }

    /** 바운딩박스 2차 필터·정렬용 임시 짝. 응답에는 노출되지 않는다. */
    private record PharmacyWithDistance(PharmacyRow row, double distanceM) {
    }
}
