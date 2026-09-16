package com.pharmaprice.drug.domain;

import com.pharmaprice.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일반의약품 마스터.
 *
 * <p>{@code category} 는 enum 이 아니라 {@code String} 이다. V1__init.sql 에
 * CHECK 제약이 없다 — T-07 시드 변환 스크립트가 식약처 데이터에서 분류를
 * 유도하므로 값 집합이 유동적이다.</p>
 */
@Entity
@Table(name = "drug")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Drug extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 식약처 품목기준코드 */
    @Column(name = "item_seq", length = 20, unique = true)
    private String itemSeq;

    /** 품목명 (예: 타이레놀정500밀리그람) */
    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /** 검색·표시용 짧은 이름 (예: 타이레놀 500mg) */
    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    /** 제조/수입사 */
    @Column(name = "maker", length = 100)
    private String maker;

    /** 해열진통 | 소화제 | 감기약 | 연고 | 소독약 | 비타민 | 기타 */
    @Column(name = "category", length = 50, nullable = false)
    private String category;

    /** 제형 (정제, 캡슐, 시럽, 연고 등) */
    @Column(name = "form", length = 50)
    private String form;

    /** 판매 단위 (예: 8정, 10ml). 가격 비교의 전제라 필수. */
    @Column(name = "package_unit", length = 50, nullable = false)
    private String packageUnit;

    /** 일반의약품 여부. false 는 적재하지 않는다. */
    @Column(name = "otc_flag", nullable = false)
    @Builder.Default
    private boolean otcFlag = true;

    /** 시드 생성용 기준가. 운영 시 미사용. */
    @Column(name = "base_price")
    private Integer basePrice;

    /** 낱알 이미지 */
    @Column(name = "image_url", length = 500)
    private String imageUrl;
}
