package com.pharmaprice.drug.repository;

import com.pharmaprice.drug.domain.Drug;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@code docs/ROADMAP.md} T-13 / {@code docs/API.md} §3 검색·상세 조회 전용 native
 * 쿼리 리포지토리. {@link DrugRepository}(T-06, CRUD 용도)와 나란히 두는 별도
 * 리포지토리다 — {@code PriceStatRepository}가 이미 보여준 "JPA 표준 리포지토리 +
 * native 집계 전용 리포지토리 공존" 패턴과 동일하다.
 *
 * <p>이 인터페이스는 조회만 하므로 {@link org.springframework.data.jpa.repository.JpaRepository}가
 * 아니라 마커 인터페이스 {@link Repository}만 확장한다 — save/delete 등 불필요한
 * CRUD 메서드를 노출하지 않기 위해서다.</p>
 *
 * <p><b>otc_flag = true 강제</b>: PRD F1-6("전문의약품은 어떤 경로로도 노출되면
 * 안 된다")에 따라 두 쿼리 모두 하드코딩으로 필터링한다(파라미터화하지 않음 —
 * 호출부 실수로 풀릴 수 있는 여지를 원천 차단).</p>
 *
 * <p><b>인덱스 참고</b>: {@code display_name}에는 pg_trgm GIN 인덱스
 * (idx_drug_display_name_trgm)가 있지만 {@code name}에는 없다({@code V1__init.sql}).
 * {@code q} 조건의 {@code name} 쪽 {@code ILIKE}는 시퀀셜 스캔이 되지만, 현재
 * 데이터 규모(수십 건)에서는 무해하다 — 인덱스 추가는 스키마 변경(V4 마이그레이션)
 * 이라 T-13 스코프 밖이며, 데이터가 커지면 재검토할 지점으로 남겨둔다.</p>
 */
public interface DrugQueryRepository extends Repository<Drug, Long> {

    /**
     * 목록/검색. {@code q}·{@code category}는 {@code null}이면 무시된다
     * ({@code (:param IS NULL OR ...)} 패턴 — {@code PriceStatRepository}의
     * {@code @Param} 널허용 스타일과 동일). {@code pharmacy_drug_price_stat}을
     * {@code drug_id}로 미리 집계한 서브쿼리와 조인 1회로만 처리해 N+1을 막는다.
     *
     * <p>{@code countSearch}와 WHERE 절이 반드시 동일해야 페이지네이션 총
     * 건수가 실제 목록과 어긋나지 않는다 — 한쪽만 고치지 않도록 주의.</p>
     */
    @Query(value = """
        SELECT d.id AS id, d.item_seq AS item_seq, d.name AS name, d.display_name AS display_name,
               d.maker AS maker, d.category AS category, d.form AS form, d.package_unit AS package_unit,
               d.image_url AS image_url,
               s.avg_price AS national_avg_price, COALESCE(s.cnt, 0) AS pharmacy_count
        FROM drug d
        LEFT JOIN (
            SELECT drug_id, AVG(rep_price)::int AS avg_price, COUNT(*) AS cnt
            FROM pharmacy_drug_price_stat
            GROUP BY drug_id
        ) s ON s.drug_id = d.id
        WHERE d.otc_flag = true
          AND (:q IS NULL OR d.display_name ILIKE '%' || :q || '%' OR d.name ILIKE '%' || :q || '%')
          AND (:category IS NULL OR d.category = :category)
        ORDER BY d.display_name, d.id
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    java.util.List<DrugSearchRow> search(@Param("q") String q, @Param("category") String category,
                                          @Param("limit") int limit, @Param("offset") long offset);

    /** {@link #search}와 동일한 WHERE 절의 총 건수. 페이지네이션 {@code totalElements}에 쓴다. */
    @Query(value = """
        SELECT COUNT(*)
        FROM drug d
        WHERE d.otc_flag = true
          AND (:q IS NULL OR d.display_name ILIKE '%' || :q || '%' OR d.name ILIKE '%' || :q || '%')
          AND (:category IS NULL OR d.category = :category)
        """, nativeQuery = true)
    long countSearch(@Param("q") String q, @Param("category") String category);

    /**
     * 상세 조회. {@code GROUP BY d.id}만으로 같은 테이블({@code drug})의 다른
     * 컬럼을 그대로 select할 수 있다 — PostgreSQL이 기본키에 대한 함수 종속성을
     * 인정하기 때문이다. {@code otc_flag = true}가 아니면(전문의약품이거나
     * 존재하지 않으면) 빈 {@link Optional}을 반환해, 컨트롤러가 404로 매핑한다.
     */
    @Query(value = """
        SELECT d.id AS id, d.item_seq AS item_seq, d.name AS name, d.display_name AS display_name,
               d.maker AS maker, d.category AS category, d.form AS form, d.package_unit AS package_unit,
               d.image_url AS image_url,
               AVG(s.rep_price)::int AS national_avg, MIN(s.rep_price)::int AS national_min,
               MAX(s.rep_price)::int AS national_max,
               COUNT(s.pharmacy_id) AS pharmacy_count, COALESCE(SUM(s.report_count), 0) AS report_count
        FROM drug d
        LEFT JOIN pharmacy_drug_price_stat s ON s.drug_id = d.id
        WHERE d.id = :drugId AND d.otc_flag = true
        GROUP BY d.id
        """, nativeQuery = true)
    Optional<DrugDetailRow> findDetail(@Param("drugId") Long drugId);

    /** {@link #search} 결과 프로젝션. {@code nationalAvgPrice}는 통계가 없으면 {@code null}. */
    interface DrugSearchRow {
        Long getId();
        String getItemSeq();
        String getName();
        String getDisplayName();
        String getMaker();
        String getCategory();
        String getForm();
        String getPackageUnit();
        String getImageUrl();
        Integer getNationalAvgPrice();
        long getPharmacyCount();
    }

    /** {@link #findDetail} 결과 프로젝션. 통계가 없으면 {@code national*}은 {@code null}, 나머지는 0. */
    interface DrugDetailRow {
        Long getId();
        String getItemSeq();
        String getName();
        String getDisplayName();
        String getMaker();
        String getCategory();
        String getForm();
        String getPackageUnit();
        String getImageUrl();
        Integer getNationalAvg();
        Integer getNationalMin();
        Integer getNationalMax();
        long getPharmacyCount();
        long getReportCount();
    }
}
