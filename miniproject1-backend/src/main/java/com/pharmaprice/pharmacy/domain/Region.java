package com.pharmaprice.pharmacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행정구역(시·군·구). 위치 권한이 거부됐을 때 폴백 좌표로 쓰인다.
 *
 * <p>PK 가 행정표준코드({@code code})를 그대로 쓰는 자연키라서, 이미 id 가
 * 채워진 채로 {@code save()} 를 호출하면 Spring Data JPA 가 이 엔티티를
 * "새것이 아님"으로 판단해 persist 대신 merge(SELECT 후 INSERT)를 호출한다.
 * 실제 적재는 T-07 시드 SQL이 담당하고 JPA 경로는 테스트뿐이라 이 SELECT
 * 한 번은 문제가 되지 않는다 — 대량 적재가 필요해지면 {@code Persistable}
 * 구현을 재검토한다.</p>
 */
@Entity
@Table(name = "region")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Region {

    /** 행정표준코드 (예: 11680 강남구) */
    @Id
    @Column(name = "code", length = 10)
    private String code;

    @Column(name = "sido", length = 20, nullable = false)
    private String sido;

    @Column(name = "sigungu", length = 30, nullable = false)
    private String sigungu;

    @Column(name = "center_lat", nullable = false)
    private double centerLat;

    @Column(name = "center_lng", nullable = false)
    private double centerLng;
}
