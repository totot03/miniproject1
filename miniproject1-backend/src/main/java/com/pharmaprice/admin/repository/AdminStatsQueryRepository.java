package com.pharmaprice.admin.repository;

import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@code docs/ROADMAP.md} T-31 / {@code docs/API.md} §8 관리자 통계 4개 엔드포인트
 * 전용 native 쿼리 리포지토리. {@code recommendation.repository.SearchQueryRepository}와
 * 마찬가지로 특정 도메인의 CRUD가 아니라 여러 테이블을 넘나드는 집계이므로, 이
 * 패키지에 별도 도메인 엔티티가 없어 {@link PharmacyDrugPriceStat}을 마커 제네릭
 * anchor로 삼는다(같은 이유로 anchor를 고른 {@code SearchQueryRepository} 전례).
 *
 * <p>조회 전용이라 {@link org.springframework.data.jpa.repository.JpaRepository}가
 * 아니라 마커 {@link Repository}만 확장한다 — {@code PharmacyQueryRepository}·
 * {@code DrugQueryRepository}와 동일한 컨벤션.</p>
 */
public interface AdminStatsQueryRepository extends Repository<PharmacyDrugPriceStat, Long> {

    /**
     * overview의 최근 7일 제보 추이. DB 세션 타임존이 이미 {@code Asia/Seoul}로
     * 고정돼 있어({@code docs/ROADMAP.md} T-02) {@code created_at::date} 캐스팅만으로
     * KST 날짜가 나온다 — {@code AT TIME ZONE} 명시는 인덱스 표현식(IMMUTABLE 요구)
     * 에서만 필요했던 특수 케이스라 여기서는 쓰지 않는다.
     */
    @Query(value = """
        SELECT created_at::date AS report_date, COUNT(*) AS report_count
        FROM price_report
        WHERE created_at::date >= CURRENT_DATE - 6
        GROUP BY created_at::date
        ORDER BY report_date
        """, nativeQuery = true)
    List<TrendRow> recentReportTrend();

    /**
     * {@code docs/DATABASE.md} §5.3 그대로에 {@code sido} 필터만 추가했다.
     * {@code r.code}·{@code d.id}가 각 테이블의 기본키라 PostgreSQL의 함수 종속성
     * 규칙에 따라 {@code GROUP BY r.code, d.id}만으로 {@code r.sido}/{@code r.sigungu}/
     * {@code d.display_name}을 추가 그룹핑 없이 그대로 SELECT할 수 있다.
     */
    @Query(value = """
        SELECT r.code AS region_code, r.sido AS sido, r.sigungu AS sigungu,
               d.id AS drug_id, d.display_name AS drug_display_name,
               ROUND(AVG(s.rep_price))::int AS avg_price,
               MIN(s.rep_price) AS min_price,
               MAX(s.rep_price) AS max_price,
               COUNT(DISTINCT s.pharmacy_id) AS pharmacy_count,
               SUM(s.report_count) AS report_count
        FROM pharmacy_drug_price_stat s
        JOIN pharmacy p ON p.id = s.pharmacy_id
        JOIN region   r ON r.code = p.region_code
        JOIN drug     d ON d.id = s.drug_id
        WHERE (:regionCode IS NULL OR p.region_code = :regionCode)
          AND (:drugId     IS NULL OR s.drug_id = :drugId)
          AND (:sido       IS NULL OR r.sido = :sido)
        GROUP BY r.code, d.id
        ORDER BY r.sido, r.sigungu, d.display_name
        """, nativeQuery = true)
    List<RegionStatRow> regionStats(@Param("regionCode") String regionCode, @Param("drugId") Long drugId,
                                     @Param("sido") String sido);

    /** 약품 1종의 500원 버킷 가격 분포. {@code pharmacy_drug_price_stat.rep_price} 기준(약국당 1건). */
    @Query(value = """
        SELECT (FLOOR(rep_price / 500) * 500)::int AS bucket_from,
               (FLOOR(rep_price / 500) * 500 + 500)::int AS bucket_to,
               COUNT(*)::int AS bucket_count
        FROM pharmacy_drug_price_stat
        WHERE drug_id = :drugId
        GROUP BY 1, 2
        ORDER BY 1
        """, nativeQuery = true)
    List<HistogramRow> drugHistogram(@Param("drugId") Long drugId);

    /** 약품 1종의 지역(시도·시군구)별 평균가. */
    @Query(value = """
        SELECT r.sido AS sido, r.sigungu AS sigungu,
               ROUND(AVG(s.rep_price))::int AS avg_price,
               COUNT(DISTINCT s.pharmacy_id) AS pharmacy_count
        FROM pharmacy_drug_price_stat s
        JOIN pharmacy p ON p.id = s.pharmacy_id
        JOIN region   r ON r.code = p.region_code
        WHERE s.drug_id = :drugId
        GROUP BY r.sido, r.sigungu
        ORDER BY r.sido, r.sigungu
        """, nativeQuery = true)
    List<DrugRegionAvgRow> drugByRegion(@Param("drugId") Long drugId);

    /**
     * 약품 1종의 전국 통계. {@code GROUP BY}가 없어 {@code PriceStatRepository.aggregate}와
     * 동일하게 대상이 0건이어도 행 하나(전부 {@code null})가 반환된다 — {@link java.util.Optional}
     * 불필요.
     */
    @Query(value = """
        SELECT AVG(rep_price)::int AS avg,
               percentile_cont(0.5) WITHIN GROUP (ORDER BY rep_price)::int AS median,
               MIN(rep_price) AS min,
               MAX(rep_price) AS max,
               ROUND(stddev_samp(rep_price))::int AS std_dev
        FROM pharmacy_drug_price_stat
        WHERE drug_id = :drugId
        """, nativeQuery = true)
    DrugNationalRow drugNational(@Param("drugId") Long drugId);

    /**
     * 지역 간 가격 격차 Top N. {@code docs/DATABASE.md} §5.4의 {@code by_region} CTE
     * (약국 3곳 미만인 지역·약품 조합 제외)는 그대로 두고, 최저가/최고가 지역명을
     * 함께 내려주기 위해 {@code ROW_NUMBER()} 윈도우 함수로 약품별 최저·최고 지역을
     * 각각 1건씩 뽑아 자기 자신과 조인한다. {@code region_count}(약품별 통과 지역 수)가
     * 3 미만이면 원 쿼리의 바깥쪽 {@code HAVING COUNT(*) >= 3}과 동일하게 제외한다.
     */
    @Query(value = """
        WITH by_region AS (
            SELECT s.drug_id, p.region_code, AVG(s.rep_price) AS region_avg
            FROM pharmacy_drug_price_stat s
            JOIN pharmacy p ON p.id = s.pharmacy_id
            GROUP BY s.drug_id, p.region_code
            HAVING COUNT(*) >= 3
        ),
        ranked AS (
            SELECT b.drug_id, b.region_avg, r.sido, r.sigungu,
                   ROW_NUMBER() OVER (PARTITION BY b.drug_id ORDER BY b.region_avg ASC)  AS rn_min,
                   ROW_NUMBER() OVER (PARTITION BY b.drug_id ORDER BY b.region_avg DESC) AS rn_max,
                   COUNT(*) OVER (PARTITION BY b.drug_id) AS region_count
            FROM by_region b
            JOIN region r ON r.code = b.region_code
        )
        SELECT
            d.id AS drug_id, d.display_name AS drug_display_name,
            lo.sido AS cheapest_sido, lo.sigungu AS cheapest_sigungu, ROUND(lo.region_avg)::int AS cheapest_avg,
            hi.sido AS priciest_sido, hi.sigungu AS priciest_sigungu, ROUND(hi.region_avg)::int AS priciest_avg,
            ROUND(hi.region_avg - lo.region_avg)::int AS gap,
            ROUND((hi.region_avg - lo.region_avg) / lo.region_avg * 100, 1) AS gap_pct
        FROM ranked lo
        JOIN ranked hi ON hi.drug_id = lo.drug_id AND hi.rn_max = 1
        JOIN drug d ON d.id = lo.drug_id
        WHERE lo.rn_min = 1 AND lo.region_count >= 3
        ORDER BY gap_pct DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<PriceGapRow> priceGaps(@Param("limit") int limit);

    /** {@link #recentReportTrend} 결과 프로젝션. */
    interface TrendRow {
        LocalDate getReportDate();
        long getReportCount();
    }

    /** {@link #regionStats} 결과 프로젝션. */
    interface RegionStatRow {
        String getRegionCode();
        String getSido();
        String getSigungu();
        Long getDrugId();
        String getDrugDisplayName();
        Integer getAvgPrice();
        Integer getMinPrice();
        Integer getMaxPrice();
        long getPharmacyCount();
        Long getReportCount();
    }

    /** {@link #drugHistogram} 결과 프로젝션. */
    interface HistogramRow {
        int getBucketFrom();
        int getBucketTo();
        int getBucketCount();
    }

    /** {@link #drugByRegion} 결과 프로젝션. */
    interface DrugRegionAvgRow {
        String getSido();
        String getSigungu();
        Integer getAvgPrice();
        long getPharmacyCount();
    }

    /** {@link #drugNational} 결과 프로젝션. 대상 약품에 통계가 없으면 전 필드 {@code null}. */
    interface DrugNationalRow {
        Integer getAvg();
        Integer getMedian();
        Integer getMin();
        Integer getMax();
        Integer getStdDev();
    }

    /** {@link #priceGaps} 결과 프로젝션. */
    interface PriceGapRow {
        Long getDrugId();
        String getDrugDisplayName();
        String getCheapestSido();
        String getCheapestSigungu();
        Integer getCheapestAvg();
        String getPriciestSido();
        String getPriciestSigungu();
        Integer getPriciestAvg();
        Integer getGap();
        Double getGapPct();
    }
}
