package com.pharmaprice.common.config;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * {@code @CreatedDate}/{@code @LastModifiedDate} 자동 채움을 활성화한다.
 *
 * <p>메인 애플리케이션 클래스가 아니라 별도 설정 클래스로 분리했다.
 * {@code @SpringBootApplication} 클래스에 직접 붙이면 {@code @WebMvcTest}
 * 같은 슬라이스 테스트가 이 클래스를 컴포넌트 스캔 루트로 잡으면서
 * {@code AuditingEntityListener} 가 요구하는 빈을 찾지 못해 컨텍스트
 * 로딩이 깨진다.</p>
 *
 * <p>커스텀 {@link DateTimeProvider} 가 필요한 이유: Spring Data 의 기본
 * {@code CurrentDateTimeProvider} 는 감사 필드에 값을 채울 때
 * {@code LocalDateTime}/{@code Instant}/{@code Date} 등으로만 변환할 수
 * 있고 {@code OffsetDateTime} 은 지원 목록에 없어 그대로 두면
 * {@code IllegalArgumentException: Cannot convert unsupported date type}
 * 으로 저장이 실패한다. 필드 타입과 정확히 같은 {@code OffsetDateTime} 을
 * 직접 제공해 변환 자체를 우회한다.</p>
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneId.of("Asia/Seoul")));
    }
}
