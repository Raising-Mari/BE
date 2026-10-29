package com.aidisciple;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 컨텍스트와 Flyway 마이그레이션이 테스트 DB 에서 정상적으로 뜨는지 확인한다. (docker compose up -d 필요) */
@SpringBootTest
@ActiveProfiles("test")
class AiDiscipleApplicationTests {

	@Test
	void contextLoads() {
	}

}
