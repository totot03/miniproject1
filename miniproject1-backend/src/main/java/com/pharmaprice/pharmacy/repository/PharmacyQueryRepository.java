package com.pharmaprice.pharmacy.repository;

import com.pharmaprice.pharmacy.domain.Pharmacy;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@code docs/ROADMAP.md} T-19 / {@code docs/API.md} §4 약국 검색·상세 전용 native
 * 쿼리 리포지토리. {@link DrugQueryRepository}(q ILIKE + LIMIT/OFFSET + 전국평균
 * 서브쿼리)와 {@code SearchQueryRepository}(바운딩박스, LIMIT/OFFSET 없음) 두
 * 패턴을 목적에 맞게 나눠 쓴다.
 *
 * <p>약국 상세(단건)의 기본 필드({@code business_hours} 등)는 이 리포지토리가
 * 아니라 {@link PharmacyRepository}(JPA)로 조회한다 — {@code business_hours}는
 * jsonb 컬럼이라 {@code Pharmacy} 엔티티의 {@code @JdbcTypeCode(SqlTypes.JSON)}
 * 매핑을 통해서만 {@code Map<String,List<String>>}으로 안전하게 변환된다. native
 * 쿼리의 인터페이스 프로젝션은 Hibernate 엔티티 매핑을 거치지 않고 JDBC
 * {@code ResultSet}을 직접 읽어 getter 타입으로 변환하므로, jsonb 컬럼은
 * PostgreSQL 드라이버가 돌려주는 {@code PGobject}/문자열을 그대로 받게 돼
 * {@code Map} 변환기가 없다 — 이 리포지토리에는 jsonb 컬럼을 포함한 프로젝션을
 * 두지 않는다. {@code region}(지연 로딩)은 {@code spring.jpa.open-in-view=false}라
 * 서비스 메서드가 {@code @Transactional}이어야 안전하게 읽힌다.
 *
 * <p>이 인터페이스는 조회만 하므로 {@link org.springframework.data.jpa.repository.JpaRepository}가
 * 아니라 마커 {@link Repository}만 확장한다.</p>
 */
public interface PharmacyQueryRepository extends Repository<Pharmacy, Long> {

    /**
     * {@code q} 전용 검색(위치 없음). {@code name}·{@code address_road} 부분 일치,
     * 이름순 정렬 후 SQL {@code LIMIT}/{@code OFFSET}으로 페이지네이션한다 —
     * 위치 기반 2차 필터링이 없어 {@link DrugQueryRepository#search}처럼 SQL에서
     * 바로 페이지를 잘라도 {@code totalElements}가 어긋나지 않는다.
     *
     * <p>{@code address_road}에는 인덱스가 없어(있는 건 {@code idx_pharmacy_name_trgm},
     * name 전용) 시퀀셜 스캔이 되지만, {@code DrugQueryRepository}의 {@code name}
     * 컬럼과 동일하게 현재 데이터 규모(약국 ~342건)에서는 무해하다.</p>
     *
     * <p>{@code countByQuery}와 WHERE 절이 반드시 동일해야 한다.</p>
     */
    @Query(value = """
        SELECT p.id AS id, p.name AS name, p.address_road AS address_road,
               p.lat AS lat, p.lng AS lng, p.phone AS phone,
               r.code AS region_code, r.sido AS sido, r.sigungu AS sigungu
        FROM pharmacy p
        LEFT JOIN region r ON r.code = p.region_code
        WHERE p.is_active = true
          AND (:q IS NULL OR p.name ILIKE '%' || :q || '%' OR p.address_road ILIKE '%' || :q || '%')
        ORDER BY p.name, p.id
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<PharmacyRow> searchByQuery(@Param("q") String q, @Param("limit") int limit, @Param("offset") long offset);

    /** {@link #searchByQuery}와 동일한 WHERE 절의 총 건수. */
    @Query(value = """
        SELECT COUNT(*)
        FROM pharmacy p
        WHERE p.is_active = true
          AND (:q IS NULL OR p.name ILIKE '%' || :q || '%' OR p.address_road ILIKE '%' || :q || '%')
        """, nativeQuery = true)
    long countByQuery(@Param("q") String q);

    /**
     * 좌표 기반 근접 검색(바운딩 박스 1차 필터). {@code q}가 있으면 이름·주소
     * 조건도 함께 AND로 건다(API.md §4 "q와 lat/lng 중 최소 하나" — 둘 다 줄 수도
     * 있다). {@code SearchQueryRepository.findCandidatesInBoundingBox}와 동일한
     * 이유로 {@code LIMIT}/{@code OFFSET}이 없다 — 호출부가
     * {@link com.pharmaprice.recommendation.distance.DistanceCalculator#distanceMeters}로
     * 반경 2차 필터링 후 Java에서 정렬·페이지네이션해야 {@code totalElements}가
     * 정확하다. 정사각형 바운딩 박스라 모서리에 반경 밖 지점이 섞이므로 2차
     * 필터링은 필수다.
     */
    @Query(value = """
        SELECT p.id AS id, p.name AS name, p.address_road AS address_road,
               p.lat AS lat, p.lng AS lng, p.phone AS phone,
               r.code AS region_code, r.sido AS sido, r.sigungu AS sigungu
        FROM pharmacy p
        LEFT JOIN region r ON r.code = p.region_code
        WHERE p.is_active = true
          AND (:q IS NULL OR p.name ILIKE '%' || :q || '%' OR p.address_road ILIKE '%' || :q || '%')
          AND p.lat BETWEEN :latMin AND :latMax
          AND p.lng BETWEEN :lngMin AND :lngMax
        """, nativeQuery = true)
    List<PharmacyRow> findCandidatesNearby(@Param("q") String q,
        @Param("latMin") double latMin, @Param("latMax") double latMax,
        @Param("lngMin") double lngMin, @Param("lngMax") double lngMax);

    /**
     * 약국 상세의 {@code drugPrices} 배열. 전국 평균은 {@code DrugQueryRepository.search}의
     * 서브쿼리와 동일한 패턴({@code pharmacy_drug_price_stat}을 {@code drug_id}로
     * 집계)을 재사용한다. {@code repPrice} 오름차순 정렬은 API.md §4 요구사항이다.
     */
    @Query(value = """
        SELECT s.drug_id AS drug_id, d.display_name AS display_name, d.package_unit AS package_unit,
               s.rep_price AS rep_price, s.min_price AS min_price, s.max_price AS max_price,
               s.avg_price AS avg_price, s.report_count AS report_count, s.last_reported_at AS last_reported_at,
               nat.national_avg AS national_avg
        FROM pharmacy_drug_price_stat s
        JOIN drug d ON d.id = s.drug_id
        LEFT JOIN (
            SELECT drug_id, AVG(rep_price)::int AS national_avg
            FROM pharmacy_drug_price_stat
            GROUP BY drug_id
        ) nat ON nat.drug_id = s.drug_id
        WHERE s.pharmacy_id = :pharmacyId
        ORDER BY s.rep_price ASC
        """, nativeQuery = true)
    List<DrugPriceRow> findDrugPrices(@Param("pharmacyId") Long pharmacyId);

    /** {@link #searchByQuery}/{@link #findCandidatesNearby} 결과 프로젝션. 지역이 없으면 region_code/sido/sigungu가 전부 null. */
    interface PharmacyRow {
        Long getId();
        String getName();
        String getAddressRoad();
        double getLat();
        double getLng();
        String getPhone();
        String getRegionCode();
        String getSido();
        String getSigungu();
    }

    /** {@link #findDrugPrices} 결과 프로젝션. {@code nationalAvg}는 통계가 없으면 {@code null}(이론상 자기 자신이 있으니 항상 존재). */
    interface DrugPriceRow {
        Long getDrugId();
        String getDisplayName();
        String getPackageUnit();
        int getRepPrice();
        int getMinPrice();
        int getMaxPrice();
        int getAvgPrice();
        int getReportCount();
        LocalDate getLastReportedAt();
        Integer getNationalAvg();
    }
}
