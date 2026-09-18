package com.pharmaprice.report.domain;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.common.domain.BaseAuditEntity;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가격 제보 (핵심 테이블).
 *
 * <p>{@code flag_reason} 은 다른 enum 컬럼들과 달리 {@code VARCHAR(100)}이라
 * {@code @Column(length = 100)} 을 명시한다 (나머지 enum 은 전부 20).</p>
 */
@Entity
@Table(name = "price_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PriceReport extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    /** 시드 데이터는 NULL 허용 */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id")
    private AppUser user;

    /** 원 단위. DB CHECK 제약으로 100~200000 범위만 허용된다. */
    @Column(name = "price", nullable = false)
    private int price;

    /** 구매일. 미입력 시 제출일 */
    @Column(name = "purchased_at", nullable = false)
    private LocalDate purchasedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20, nullable = false)
    @Builder.Default
    private ReportSource source = ReportSource.FORM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private ReportStatus status = ReportStatus.ACTIVE;

    /** 이상치 자동 탐지 결과 */
    @Column(name = "flagged", nullable = false)
    @Builder.Default
    private boolean flagged = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_reason", length = 100)
    private FlagReason flagReason;

    /** 영수증 (선택) */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "receipt_file_id")
    private UploadedFile receiptFile;

    /** 사용자 자유 입력 */
    @Column(name = "memo", length = 200)
    private String memo;

    /** 관리자가 숨김 처리할 때 호출한다. */
    public void hide() {
        this.status = ReportStatus.HIDDEN;
    }

    /** 관리자가 반려할 때 호출한다. 작성자의 report_count 를 -1 해야 한다면 호출부에서 함께 처리한다. */
    public void reject() {
        this.status = ReportStatus.REJECTED;
    }

    public void restore() {
        this.status = ReportStatus.ACTIVE;
    }

    /** 이상치 자동 탐지 결과를 반영한다. */
    public void flag(FlagReason reason) {
        this.flagged = true;
        this.flagReason = reason;
    }

    /** 관리자가 이상치 플래그를 해제할 때 호출한다. */
    public void unflag() {
        this.flagged = false;
        this.flagReason = null;
    }
}
