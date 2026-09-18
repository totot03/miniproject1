package com.pharmaprice.recommendation.service;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.common.exception.InvalidRequestException;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.recommendation.distance.DistanceCalculator;
import com.pharmaprice.recommendation.distance.DistanceCalculator.BoundingBox;
import com.pharmaprice.recommendation.dto.DataSource;
import com.pharmaprice.recommendation.dto.SearchResponse;
import com.pharmaprice.recommendation.dto.SearchResponse.DrugInfo;
import com.pharmaprice.recommendation.dto.SearchResponse.PharmacyInfo;
import com.pharmaprice.recommendation.dto.SearchResponse.PriceInfo;
import com.pharmaprice.recommendation.dto.SearchResponse.QueryInfo;
import com.pharmaprice.recommendation.dto.SearchResponse.SearchResultItem;
import com.pharmaprice.recommendation.dto.SearchResponse.SearchSuggestion;
import com.pharmaprice.recommendation.dto.SearchResponse.SearchSummary;
import com.pharmaprice.recommendation.exception.DrugNotFoundException;
import com.pharmaprice.recommendation.exception.InvalidRadiusException;
import com.pharmaprice.recommendation.repository.SearchQueryRepository;
import com.pharmaprice.recommendation.repository.SearchQueryRepository.CandidateRow;
import com.pharmaprice.recommendation.service.ScoreCalculator.Candidate;
import com.pharmaprice.recommendation.service.ScoreCalculator.ScoredCandidate;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@code docs/ROADMAP.md} T-15 / {@code docs/API.md} §5. 지금까지 따로 만든
 * 조각(T-09 거리, T-10 통계 캐시 읽기, T-11 Score)을 하나로 묶는다 — 새로 계산하는
 * 로직은 여기 없고, 후보를 모아 {@link ScoreCalculator}에 넘기고 응답을 조립하는
 * 것이 이 클래스의 유일한 책임이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    /**
     * 검색 반경 단계이자 {@code docs/API.md} §5 허용값. {@code HaversineDistanceCalculator}는
     * 여러 API가 공유하는 순수 기하 유틸이라 이 enum을 모르므로, 검증은 이 클래스가
     * 직접 한다({@link #search} 참고).
     */
    private static final List<Integer> RADIUS_STEPS = List.of(500, 1000, 2000, 5000);

    private static final int MAX_LIMIT = 50;

    private static final Comparator<ScoredCandidate> SCORE_DESC =
        Comparator.comparingDouble(ScoredCandidate::score).reversed();
    private static final Comparator<ScoredCandidate> PRICE_ASC =
        Comparator.comparingInt(sc -> sc.candidate().repPrice());
    private static final Comparator<ScoredCandidate> DISTANCE_ASC =
        Comparator.comparingDouble(sc -> sc.candidate().distanceM());
    private static final Comparator<ScoredCandidate> PHARMACY_ID_ASC =
        Comparator.comparingLong(sc -> sc.candidate().pharmacyId());

    private final DrugRepository drugRepository;
    private final RegionRepository regionRepository;
    private final SearchQueryRepository searchQueryRepository;
    private final DistanceCalculator distanceCalculator;
    private final ScoreCalculator scoreCalculator;
    private final RecommendationProperties properties;

    @Override
    public SearchResponse search(Long drugId, Double lat, Double lng, String regionCode, int radius,
                                  String sortParam, int limit) {
        Drug drug = drugRepository.findById(drugId)
            .filter(Drug::isOtcFlag)
            .orElseThrow(() -> new DrugNotFoundException("drug not found: " + drugId));
        LocationResolution location = resolveLocation(lat, lng, regionCode);
        if (!RADIUS_STEPS.contains(radius)) {
            throw new InvalidRadiusException(
                "허용되지 않은 반경입니다: " + radius + " (허용값: " + RADIUS_STEPS + ")");
        }
        SortOption sort = SortOption.from(sortParam);
        int clampedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        List<CandidateContext> contexts = findCandidateContexts(drugId, location.lat(), location.lng(), radius);
        DrugInfo drugInfo = toDrugInfo(drug);
        QueryInfo queryInfo = toQueryInfo(location, radius, sort);

        if (contexts.isEmpty()) {
            SearchSuggestion suggestion = buildSuggestion(drugId, location, radius);
            SearchSummary emptySummary = new SearchSummary(0, null, null, null, null);
            return new SearchResponse(drugInfo, queryInfo, emptySummary, DataSource.SEED, List.of(), suggestion);
        }

        List<Candidate> candidates = contexts.stream().map(CandidateContext::toCandidate).toList();
        List<ScoredCandidate> scoreRanked = scoreCalculator.rank(candidates, radius, LocalDate.now());
        log.debug("search scored candidates: drugId={} radius={} results={}", drugId, radius, scoreRanked);

        long recommendedPharmacyId = scoreRanked.get(0).candidate().pharmacyId();
        List<ScoredCandidate> sorted = applySort(scoreRanked, sort);
        List<ScoredCandidate> limited = sorted.stream().limit(clampedLimit).toList();

        int candidateAvg = (int) Math.round(candidates.stream().mapToInt(Candidate::repPrice).average().orElseThrow());
        int candidateMin = candidates.stream().mapToInt(Candidate::repPrice).min().orElseThrow();
        int candidateMax = candidates.stream().mapToInt(Candidate::repPrice).max().orElseThrow();
        SearchSummary summary = new SearchSummary(limited.size(), candidateAvg, candidateMin, candidateMax,
            candidateMax - candidateMin);

        Map<Long, CandidateContext> contextByPharmacyId = contexts.stream()
            .collect(Collectors.toMap(ctx -> ctx.row().getPharmacyId(), Function.identity()));
        List<Long> resultPharmacyIds = limited.stream().map(sc -> sc.candidate().pharmacyId()).toList();
        DataSource dataSource = resolveDataSource(drugId, resultPharmacyIds);

        List<SearchResultItem> results = toResultItems(limited, contextByPharmacyId, candidateAvg, recommendedPharmacyId);

        return new SearchResponse(drugInfo, queryInfo, summary, dataSource, results, null);
    }

    /**
     * 바운딩 박스(1차) + 정확 거리(2차, {@code DistanceCalculator}) 필터를 거친 후보.
     * {@code suggestion} 계산(반경 확대 시뮬레이션)도 반경만 바꿔 이 메서드를 재사용한다 —
     * 후보 규모가 수십~수백 건 수준이라 재조회 비용이 무시할 만하다.
     */
    private List<CandidateContext> findCandidateContexts(Long drugId, double lat, double lng, int radius) {
        BoundingBox box = distanceCalculator.boundingBox(lat, lng, radius);
        List<CandidateRow> rows = searchQueryRepository.findCandidatesInBoundingBox(
            drugId, box.minLat(), box.maxLat(), box.minLng(), box.maxLng());
        return rows.stream()
            .map(row -> new CandidateContext(row, distanceCalculator.distanceMeters(lat, lng, row.getLat(), row.getLng())))
            .filter(ctx -> ctx.distanceM() <= radius)
            .toList();
    }

    /** F3-9: 결과 0건이면 다음 반경 단계로 확대했을 때의 실제 예상 건수를 계산한다. 이미 최댓값이면 확대할 단계가 없어 {@code null}. */
    private SearchSuggestion buildSuggestion(Long drugId, LocationResolution location, int radius) {
        OptionalInt nextRadius = RADIUS_STEPS.stream().filter(r -> r > radius).mapToInt(Integer::intValue).min();
        if (nextRadius.isEmpty()) {
            return null;
        }
        int expandedRadius = nextRadius.getAsInt();
        long estimatedCount = findCandidateContexts(drugId, location.lat(), location.lng(), expandedRadius).size();
        return new SearchSuggestion("EXPAND_RADIUS", expandedRadius, estimatedCount);
    }

    /**
     * {@code sort} 파라미터에 따라 리스트 순서만 바꾼다. {@code score}/{@code badges}/
     * {@code recommended} 값 자체는 {@link ScoreCalculator}가 SCORE 기준으로 계산한
     * 그대로 유지한다(ROADMAP T-15 "Score는 항상 계산해 응답에 넣되 정렬 기준만 바꾼다").
     */
    private List<ScoredCandidate> applySort(List<ScoredCandidate> scoreRanked, SortOption sort) {
        return switch (sort) {
            case SCORE -> scoreRanked; // ScoreCalculatorImpl이 이미 score DESC 기준으로 정렬해 반환
            case PRICE -> scoreRanked.stream()
                .sorted(PRICE_ASC.thenComparing(SCORE_DESC).thenComparing(DISTANCE_ASC).thenComparing(PHARMACY_ID_ASC))
                .toList();
            case DISTANCE -> scoreRanked.stream()
                .sorted(DISTANCE_ASC.thenComparing(SCORE_DESC).thenComparing(PRICE_ASC).thenComparing(PHARMACY_ID_ASC))
                .toList();
        };
    }

    /**
     * 응답에 실제로 포함된(limit 적용 후) 약국들의 제보 출처를 판정한다.
     * SEED만 있으면 SEED, 그 외만 있으면 USER, 섞이면 MIXED. 결과가 없으면(0건
     * 응답 경로는 여기까지 오지 않지만 방어적으로) SEED를 기본값으로 둔다.
     */
    private DataSource resolveDataSource(Long drugId, List<Long> pharmacyIds) {
        if (pharmacyIds.isEmpty()) {
            return DataSource.SEED;
        }
        List<String> sources = searchQueryRepository.findDistinctSources(
            drugId, pharmacyIds, properties.priceWindowFallbackDays());
        boolean hasSeed = sources.contains("SEED");
        boolean hasNonSeed = sources.stream().anyMatch(s -> !"SEED".equals(s));
        if (hasSeed && hasNonSeed) {
            return DataSource.MIXED;
        }
        return hasNonSeed ? DataSource.USER : DataSource.SEED;
    }

    private List<SearchResultItem> toResultItems(List<ScoredCandidate> limited,
                                                  Map<Long, CandidateContext> contextByPharmacyId,
                                                  int candidateAvg, long recommendedPharmacyId) {
        LocalDate today = LocalDate.now();
        return IntStream.range(0, limited.size())
            .mapToObj(i -> toResultItem(i + 1, limited.get(i), contextByPharmacyId, candidateAvg, recommendedPharmacyId, today))
            .toList();
    }

    private SearchResultItem toResultItem(int rank, ScoredCandidate sc, Map<Long, CandidateContext> contextByPharmacyId,
                                           int candidateAvg, long recommendedPharmacyId, LocalDate today) {
        CandidateRow row = contextByPharmacyId.get(sc.candidate().pharmacyId()).row();
        PharmacyInfo pharmacy = new PharmacyInfo(
            row.getPharmacyId(), row.getName(), row.getAddressRoad(), row.getLat(), row.getLng(), row.getPhone());
        PriceInfo price = new PriceInfo(
            row.getRepPrice(), row.getMinPrice(), row.getAvgPrice(), candidateAvg - row.getRepPrice(),
            row.getReportCount(), row.getLastReportedAt(), ChronoUnit.DAYS.between(row.getLastReportedAt(), today));
        boolean recommended = sc.candidate().pharmacyId() == recommendedPharmacyId;
        return new SearchResultItem(rank, recommended, pharmacy, price,
            Math.round(sc.candidate().distanceM()), sc.score(), sc.breakdown(), sc.badges());
    }

    private LocationResolution resolveLocation(Double lat, Double lng, String regionCode) {
        if (lat != null && lng != null) {
            DistanceCalculator.validateCoordinate(lat, lng); // 범위 밖이면 InvalidCoordinateException(400) — GlobalExceptionHandler가 변환
            return new LocationResolution(lat, lng, "GPS");
        }
        if (regionCode != null && !regionCode.isBlank()) {
            Region region = regionRepository.findById(regionCode)
                .orElseThrow(() -> new InvalidRequestException("invalid regionCode: " + regionCode));
            return new LocationResolution(region.getCenterLat(), region.getCenterLng(), "REGION");
        }
        throw new InvalidRequestException("lat/lng 또는 regionCode 중 하나는 필수입니다.");
    }

    private DrugInfo toDrugInfo(Drug drug) {
        return new DrugInfo(drug.getId(), drug.getDisplayName(), drug.getPackageUnit(), drug.getImageUrl());
    }

    private QueryInfo toQueryInfo(LocationResolution location, int radius, SortOption sort) {
        return new QueryInfo(location.lat(), location.lng(), radius, sort.name(), location.locationSource());
    }

    /** GPS 좌표 또는 지역 폴백 좌표로 정규화된 위치. {@code locationSource}는 {@code "GPS"} \| {@code "REGION"}. */
    private record LocationResolution(double lat, double lng, String locationSource) {
    }

    /** 바운딩 박스+정확 거리 필터를 통과한 후보 1건. distanceM은 반경 필터에 쓰인 정확 거리다. */
    private record CandidateContext(CandidateRow row, double distanceM) {
        Candidate toCandidate() {
            return new Candidate(row.getPharmacyId(), row.getRepPrice(), distanceM, row.getLastReportedAt(), row.getReportCount());
        }
    }

    /** {@code sort} 쿼리 파라미터 정규화. 알 수 없는 값은 SCORE로 취급한다(400을 내지 않음). */
    private enum SortOption {
        SCORE, PRICE, DISTANCE;

        static SortOption from(String raw) {
            if (raw == null) {
                return SCORE;
            }
            try {
                return SortOption.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return SCORE;
            }
        }
    }
}
