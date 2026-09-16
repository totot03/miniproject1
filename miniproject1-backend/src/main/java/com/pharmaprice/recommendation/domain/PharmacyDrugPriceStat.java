package com.pharmaprice.recommendation.domain;

import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가격 통계 캐시. 검색 경로에서 읽는 유일한 집계 테이블이다.
 *
 * <p>{@code created_at} 이 없고 {@code calculated_at} 만 있어
 * {@code BaseTimeEntity} 를 상속하지 않는다. T-10 의 재계산 서비스가
 * {@code INSERT ... ON CONFLICT (pharmacy_id, drug_id) DO UPDATE} (native)
 * 로 이 행을 upsert 할 예정이라 JPA 감사 리스너가 개입할 경로가 없으므로,
 * {@code calculated_at} 도 일반 필드로 두고 DB의 {@code DEFAULT now()} 와
 * 서비스 코드에 맡긴다.</p>
 */
@Entity
@Table(
    name = "pharmacy_drug_price_stat",
    uniqueConstraints = @UniqueConstraint(name = "uq_stat_pair", columnNames = {"pharmacy_id", "drug_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PharmacyDrugPriceStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    /** 대표가격 = 유효 제보의 중앙값 */
    @Column(name = "rep_price", nullable = false)
    private int repPrice;

    /** 유효 제보 최저가 */
    @Column(name = "min_price", nullable = false)
    private int minPrice;

    /** 유효 제보 최고가 */
    @Column(name = "max_price", nullable = false)
    private int maxPrice;

    /** 유효 제보 평균 (표시용) */
    @Column(name = "avg_price", nullable = false)
    private int avgPrice;

    /** 유효 제보 수 */
    @Column(name = "report_count", nullable = false)
    private int reportCount;

    /** 가장 최근 유효 제보의 purchased_at. 신선도 계산 입력 */
    @Column(name = "last_reported_at", nullable = false)
    private LocalDate lastReportedAt;

    /** 계산에 사용한 창 크기 (90 또는 180) */
    @Column(name = "window_days", nullable = false)
    private short windowDays;

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;
}
