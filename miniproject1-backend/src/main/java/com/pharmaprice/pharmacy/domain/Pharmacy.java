package com.pharmaprice.pharmacy.domain;

import com.pharmaprice.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 약국.
 *
 * <p>{@code business_hours} 는 Hibernate 7 의 {@code @JdbcTypeCode(SqlTypes.JSON)}
 * 으로 매핑한다. {@code PostgreSQLDialect} 가 {@code SqlTypes.JSON} 의 DDL
 * 타입을 이미 {@code "jsonb"} 로 등록해 두었으므로 {@code columnDefinition}
 * 지정은 불필요하다.</p>
 */
@Entity
@Table(name = "pharmacy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Pharmacy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 심평원 요양기관기호. 공공데이터 재적재 시 멱등성 키 */
    @Column(name = "hira_code", length = 30, unique = true)
    private String hiraCode;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /** 도로명 주소 */
    @Column(name = "address_road", length = 255)
    private String addressRoad;

    /** 지번 주소 */
    @Column(name = "address_jibun", length = 255)
    private String addressJibun;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "region_code")
    private Region region;

    /** 위도 (WGS84) */
    @Column(name = "lat", nullable = false)
    private double lat;

    /** 경도 (WGS84) */
    @Column(name = "lng", nullable = false)
    private double lng;

    @Column(name = "phone", length = 20)
    private String phone;

    /** {"mon":["09:00","19:00"], ..., "holiday":null} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_hours")
    private Map<String, List<String>> businessHours;

    /** 폐업 시 false */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public void deactivate() {
        this.active = false;
    }
}
