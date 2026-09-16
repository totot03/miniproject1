package com.pharmaprice.recommendation.repository;

import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@code docs/ROADMAP.md} T-15 / {@code docs/API.md} §5 최저가 검색 전용 native
 * 쿼리 리포지토리. {@code docs/DATABASE.md} §5.1 SQL에서 Score 계산·정렬 부분만
 * 뺀 버전이다 — Score/정렬은 {@link com.pharmaprice.recommendation.service.ScoreCalculator}
 * (T-11)가 Java에서 전담한다({@code DATABASE.md} §5.1이 스스로 "후보가 수백 건
 * 수준이면 이쪽이 테스트하기 훨씬 쉽다"고 제시한 대안).
 *
 * <p>{@link DrugQueryRepository} 패턴(마커 {@link Repository}, native query +
 * 인터페이스 프로젝션)과 동일하다.</p>
 */
public interface SearchQueryRepository extends Repository<PharmacyDrugPriceStat, Long> {

    /**
     * 바운딩 박스({@code docs/DATABASE.md} §4.1) 안의 후보만 1차로 좁힌다.
     * 정사각형이라 모서리에 반경 밖 지점이 섞이므로, 호출부가
     * {@link com.pharmaprice.recommendation.distance.DistanceCalculator#distanceMeters}
     * 로 2차 필터링을 반드시 해야 한다. {@code pharmacy_drug_price_stat}에 행이
     * 없는(= 유효 제보 0건, T-10이 삭제) 약국은 조인만으로 자동 제외된다.
     */
    @Query(value = """
        SELECT p.id AS pharmacy_id, p.name AS name, p.address_road AS address_road,
               p.lat AS lat, p.lng AS lng, p.phone AS phone,
               s.rep_price AS rep_price, s.min_price AS min_price, s.avg_price AS avg_price,
               s.report_count AS report_count, s.last_reported_at AS last_reported_at
        FROM pharmacy_drug_price_stat s
        JOIN pharmacy p ON p.id = s.pharmacy_id
        WHERE s.drug_id = :drugId AND p.is_active = true
          AND p.lat BETWEEN :latMin AND :latMax
          AND p.lng BETWEEN :lngMin AND :lngMax
        """, nativeQuery = true)
    List<CandidateRow> findCandidatesInBoundingBox(@Param("drugId") Long drugId,
        @Param("latMin") double latMin, @Param("latMax") double latMax,
        @Param("lngMin") double lngMin, @Param("lngMax") double lngMax);

    /**
     * 응답에 실제로 포함된(limit 적용 후) 약국들의 최근 {@code maxWindowDays}일
     * 유효 제보 {@code source} 종류를 구해 {@code dataSource}(SEED/MIXED/USER)
     * 판정에 쓴다. 약국마다 {@code pharmacy_drug_price_stat.window_days}가
     * 달라도 항상 한 창(180일, {@code priceWindowFallbackDays})으로 근사한다 —
     * 이 필드는 "SEED 데이터가 섞였는가"를 알리는 고지 배너 트리거일 뿐이라
     * 대표가격 계산에 실제로 기여한 제보만 정밀 추적할 필요는 없다.
     */
    @Query(value = """
        SELECT DISTINCT source FROM price_report
        WHERE drug_id = :drugId AND pharmacy_id IN (:pharmacyIds)
          AND status = 'ACTIVE' AND flagged = false
          AND purchased_at >= CURRENT_DATE - :maxWindowDays
        """, nativeQuery = true)
    List<String> findDistinctSources(@Param("drugId") Long drugId, @Param("pharmacyIds") List<Long> pharmacyIds,
        @Param("maxWindowDays") int maxWindowDays);

    /** {@link #findCandidatesInBoundingBox} 결과 프로젝션. */
    interface CandidateRow {
        Long getPharmacyId();
        String getName();
        String getAddressRoad();
        double getLat();
        double getLng();
        String getPhone();
        int getRepPrice();
        int getMinPrice();
        int getAvgPrice();
        int getReportCount();
        LocalDate getLastReportedAt();
    }
}
