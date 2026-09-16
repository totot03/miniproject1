package com.pharmaprice.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 생성 시각(created_at)만 갖는 테이블을 위한 감사 기반 클래스.
 * refresh_token / pharmacy / drug / uploaded_file 이 여기에 해당한다.
 *
 * <p>region 과 pharmacy_drug_price_stat 은 시각 컬럼 구성이 달라(없거나
 * calculated_at 처럼 이름이 다름) 이 클래스를 상속하지 않는다.</p>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
