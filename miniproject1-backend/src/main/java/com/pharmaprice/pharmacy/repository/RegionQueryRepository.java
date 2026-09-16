package com.pharmaprice.pharmacy.repository;

import com.pharmaprice.pharmacy.domain.Region;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * {@code docs/ROADMAP.md} T-14 / {@code docs/API.md} §7 지역 목록 전용 native
 * 쿼리 리포지토리. {@link RegionRepository}(T-06, CRUD 용도)와 나란히 두는
 * 별도 리포지토리다 — {@code DrugQueryRepository}가 이미 보여준 "JPA 표준
 * 리포지토리 + native 집계 전용 리포지토리 공존" 패턴과 동일하다.
 *
 * <p>이 인터페이스는 조회만 하므로 {@link org.springframework.data.jpa.repository.JpaRepository}가
 * 아니라 마커 인터페이스 {@link Repository}만 확장한다.</p>
 */
public interface RegionQueryRepository extends Repository<Region, String> {

    /**
     * 전체 지역을 {@code pharmacyCount}(활성 약국 수)와 함께 시도·시군구 순으로
     * 정렬해 반환한다. {@code LEFT JOIN}이 핵심이다 — 약국이 하나도 없는 지역도
     * 결과에 남아야 프론트에서 "약국 없음" 비활성 처리를 할 수 있다
     * ({@code docs/ROADMAP.md} T-14 구현 가이드 2번). 폐업 약국({@code is_active = false})은
     * 조인 조건에서 미리 제외해 카운트에 잡히지 않는다.
     *
     * <p>결과 순서(시도 → 시군구)는 {@link com.pharmaprice.pharmacy.service.RegionServiceImpl}가
     * 시도별 그룹핑 시 그대로 신뢰하는 전제다 — 이 ORDER BY를 지우면 그룹 순서가
     * 흐트러진다.</p>
     */
    @Query(value = """
        SELECT r.code AS code, r.sido AS sido, r.sigungu AS sigungu,
               r.center_lat AS center_lat, r.center_lng AS center_lng,
               COUNT(p.id) AS pharmacy_count
        FROM region r
        LEFT JOIN pharmacy p ON p.region_code = r.code AND p.is_active = true
        GROUP BY r.code, r.sido, r.sigungu, r.center_lat, r.center_lng
        ORDER BY r.sido, r.sigungu
        """, nativeQuery = true)
    List<RegionRow> listWithPharmacyCount();

    /** {@link #listWithPharmacyCount} 결과 프로젝션. */
    interface RegionRow {
        String getCode();
        String getSido();
        String getSigungu();
        double getCenterLat();
        double getCenterLng();
        long getPharmacyCount();
    }
}
