package com.pharmaprice.auth.domain;

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
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리프레시 토큰. 로그아웃 시 서버에서 토큰을 무효화하기 위해 발급분을
 * 보관한다. 원문은 저장하지 않고 SHA-256 해시만 저장한다.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** 토큰 원문의 SHA-256. 원문은 저장하지 않는다. */
    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    /** 발급 + 14일 */
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** 로그아웃 시각. NULL 이면 유효 */
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    /** 로그아웃 시 호출한다. */
    public void revoke(OffsetDateTime at) {
        this.revokedAt = at;
    }

    public boolean isValid(OffsetDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
