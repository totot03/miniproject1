package com.pharmaprice;

import org.junit.jupiter.api.Test;

/**
 * 전체 스프링 컨텍스트가 뜨는지만 확인한다.
 *
 * <p>{@link AbstractIntegrationTest} 를 상속해 test 프로파일 +
 * {@code pharmaprice_test} DB 로 붙는다. 상속하지 않으면 개발 DB
 * ({@code pharmaprice}) 에 직접 접속해버린다.</p>
 */
class MiniProject1BackendApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
