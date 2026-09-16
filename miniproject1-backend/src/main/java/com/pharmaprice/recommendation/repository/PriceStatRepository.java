package com.pharmaprice.recommendation.repository;

import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * T-06 은 골격과 기본 조회 메서드만 담당했다. IQR·중앙값 재계산 native
 * 쿼리와 {@code ON CONFLICT} upsert는 T-10 에서 이 인터페이스에
 * 덧붙인다 — 새 리포지토리를 만들지 않는다.
 */
public interface PriceStatRepository extends JpaRepository<PharmacyDrugPriceStat, Long> {

    /** T-10 재계산 서비스가 (약국, 약품) 조합의 기존 통계 행을 찾을 때 쓴다. */
    Optional<PharmacyDrugPriceStat> findByPharmacyIdAndDrugId(Long pharmacyId, Long drugId);

    /**
     * {@code docs/DATABASE.md} §5.2. {@code windowDays} 안의 유효 제보(ACTIVE,
     * 미플래그)에 대해 4건 이상이면 IQR 이상치를 제거하고, 남은 값의 중앙값·
     * 최소·최대·평균·건수를 한 번에 계산한다.
     *
     * <p>{@code GROUP BY} 없는 집계라 대상이 0건이어도 행 하나는 반환된다
     * (이 경우 {@code reportCount == 0}이고 나머지 필드는 모두 {@code null}).
     * 그래서 반환 타입에 {@link Optional}이 필요 없다.</p>
     */
    @Query(value = """
        WITH valid AS (
            SELECT price FROM price_report
            WHERE pharmacy_id = :pharmacyId AND drug_id = :drugId
              AND status = 'ACTIVE' AND flagged = false
              AND purchased_at >= CURRENT_DATE - :windowDays
        ),
        q AS (
            SELECT percentile_cont(0.25) WITHIN GROUP (ORDER BY price) AS q1,
                   percentile_cont(0.75) WITHIN GROUP (ORDER BY price) AS q3,
                   count(*) AS n
            FROM valid
        ),
        trimmed AS (
            SELECT v.price FROM valid v CROSS JOIN q
            WHERE q.n < 4
               OR v.price BETWEEN q.q1 - 1.5 * (q.q3 - q.q1) AND q.q3 + 1.5 * (q.q3 - q.q1)
        )
        SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY price)::int AS repPrice,
               MIN(price)::int AS minPrice, MAX(price)::int AS maxPrice,
               AVG(price)::int AS avgPrice, COUNT(*)::int AS reportCount
        FROM trimmed
        """, nativeQuery = true)
    PriceAggregate aggregate(@Param("pharmacyId") Long pharmacyId, @Param("drugId") Long drugId,
                             @Param("windowDays") int windowDays);

    /**
     * {@code aggregate}와 같은 조건(윈도우 포함)의 {@code MAX(purchased_at)}.
     * {@code DATABASE.md} §5.2가 "같은 조건의 MAX(purchased_at)으로 별도 조회한다"고
     * 명시한 대로, IQR로 걸러진 {@code trimmed}가 아니라 {@code valid} 기준이다.
     */
    @Query(value = """
        SELECT MAX(purchased_at) FROM price_report
        WHERE pharmacy_id = :pharmacyId AND drug_id = :drugId
          AND status = 'ACTIVE' AND flagged = false
          AND purchased_at >= CURRENT_DATE - :windowDays
        """, nativeQuery = true)
    LocalDate findLastReportedAt(@Param("pharmacyId") Long pharmacyId, @Param("drugId") Long drugId,
                                  @Param("windowDays") int windowDays);

    /**
     * {@link PharmacyDrugPriceStat} 클래스 javadoc이 예고한 upsert.
     *
     * <p>native 쓰기는 영속성 컨텍스트(1차 캐시)가 전혀 알지 못하므로,
     * {@code clearAutomatically}로 캐시를 비워 이후 조회가 항상 DB 최신값을
     * 반영하게 한다. 그렇지 않으면 같은 트랜잭션 안에서 이 upsert 이전에
     * 로드된 인스턴스가 갱신 전 값으로 그대로 반환될 수 있다.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO pharmacy_drug_price_stat
            (pharmacy_id, drug_id, rep_price, min_price, max_price, avg_price, report_count, last_reported_at, window_days, calculated_at)
        VALUES (:pharmacyId, :drugId, :repPrice, :minPrice, :maxPrice, :avgPrice, :reportCount, :lastReportedAt, :windowDays, now())
        ON CONFLICT (pharmacy_id, drug_id) DO UPDATE SET
            rep_price = EXCLUDED.rep_price, min_price = EXCLUDED.min_price, max_price = EXCLUDED.max_price,
            avg_price = EXCLUDED.avg_price, report_count = EXCLUDED.report_count,
            last_reported_at = EXCLUDED.last_reported_at, window_days = EXCLUDED.window_days,
            calculated_at = EXCLUDED.calculated_at
        """, nativeQuery = true)
    void upsert(@Param("pharmacyId") Long pharmacyId, @Param("drugId") Long drugId,
                @Param("repPrice") int repPrice, @Param("minPrice") int minPrice, @Param("maxPrice") int maxPrice,
                @Param("avgPrice") int avgPrice, @Param("reportCount") int reportCount,
                @Param("lastReportedAt") LocalDate lastReportedAt, @Param("windowDays") short windowDays);

    /** 유효 제보가 0이 되면 검색 결과에서 자동으로 빠지도록 행 자체를 삭제한다(ck_stat_report_count > 0 제약과 정합). */
    void deleteByPharmacyIdAndDrugId(Long pharmacyId, Long drugId);

    /** {@link #aggregate} 결과 프로젝션. {@code reportCount == 0}이면 나머지 필드는 모두 {@code null}이다(대상 제보가 없다는 뜻). */
    interface PriceAggregate {
        Integer getRepPrice();
        Integer getMinPrice();
        Integer getMaxPrice();
        Integer getAvgPrice();
        int getReportCount();
    }
}
