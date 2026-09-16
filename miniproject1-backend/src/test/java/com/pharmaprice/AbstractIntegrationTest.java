package com.pharmaprice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 테스트 베이스 클래스.
 *
 * <p>Docker 가 없어 Testcontainers({@code @Container @ServiceConnection
 * PostgreSQLContainer})를 쓸 수 없다 (docs/ROADMAP.md T-06 메모의 폴백 경로).
 * 대신 이미 준비된 빈 {@code pharmaprice_test} DB 를
 * {@code src/test/resources/application-test.yml} 로 붙이고, 테스트마다
 * {@code @Transactional} 롤백으로 격리한다.</p>
 *
 * <p>⚠️ 롤백은 flush 를 건너뛴다. 저장이 실제로 DB 에 닿았는지 확인하려면
 * 저장 직후 {@code EntityManager.flush()} + {@code clear()} 를 호출해
 * 1차 캐시를 비운 뒤 재조회해야 한다. 그렇지 않으면 매핑이 틀려도
 * 영속성 컨텍스트에서 그대로 값을 읽어와 테스트가 통과해 버린다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {
}
