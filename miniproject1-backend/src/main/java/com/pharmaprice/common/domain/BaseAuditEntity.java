package com.pharmaprice.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * 생성 시각(created_at)과 수정 시각(updated_at)을 모두 갖는 테이블을 위한
 * 감사 기반 클래스. app_user / price_report 가 여기에 해당한다.
 *
 * <p>{@link BaseTimeEntity} 와 별도 클래스로 나눈 이유: updated_at 컬럼이
 * 없는 pharmacy / drug / uploaded_file / refresh_token 에서 이 필드를
 * 상속하면 스키마 검증({@code ddl-auto: validate})이 "컬럼 없음"으로
 * 실패한다.</p>
 */
@Getter
@MappedSuperclass
public abstract class BaseAuditEntity extends BaseTimeEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
