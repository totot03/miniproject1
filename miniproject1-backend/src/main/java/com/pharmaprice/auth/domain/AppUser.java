package com.pharmaprice.auth.domain;

import com.pharmaprice.common.domain.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 사용자. {@code user} 는 PostgreSQL 예약어라 테이블명은 {@code app_user} 를 쓴다.
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AppUser extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 ID */
    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    /** BCrypt (strength 10) */
    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    /** 제보자 표시명 */
    @Column(name = "nickname", length = 30, nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * 누적 제보 수 (비정규화). 제보 생성 트랜잭션에서 +1,
     * 관리자가 REJECTED 처리 시 -1 한다. {@code GET /auth/me} 응답에 쓰인다.
     */
    @Column(name = "report_count", nullable = false)
    @Builder.Default
    private int reportCount = 0;

    /** 제보 생성 트랜잭션에서 호출한다. */
    public void increaseReportCount() {
        this.reportCount++;
    }

    /** 관리자가 제보를 REJECTED 로 전환할 때 호출한다. */
    public void decreaseReportCount() {
        this.reportCount = Math.max(0, this.reportCount - 1);
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }
}
