package com.pharmaprice.report.domain;

import com.pharmaprice.auth.domain.AppUser;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업로드 파일 (영수증 등). {@code price_report} 보다 먼저 생성돼야 하는
 * FK 참조 대상이라 V1__init.sql 에서도 먼저 정의됐다.
 */
@Entity
@Table(name = "uploaded_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UploadedFile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_name", length = 255, nullable = false)
    private String originalName;

    /** 예: /app/uploads/2026/09/{uuid}.jpg */
    @Column(name = "stored_path", length = 500, nullable = false)
    private String storedPath;

    /** image/jpeg 등 */
    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    /** 5MB 이하 */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "uploaded_by")
    private AppUser uploadedBy;
}
