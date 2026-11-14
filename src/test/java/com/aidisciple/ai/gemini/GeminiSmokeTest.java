package com.aidisciple.ai.gemini;

import java.util.Map;
import java.util.TreeMap;

import com.aidisciple.ai.AiException;
import com.aidisciple.ai.AiProperties;
import com.aidisciple.ai.AiPurpose;
import com.aidisciple.ai.LlmClient;
import com.aidisciple.ai.LlmRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Gemini 스모크 테스트 (수동 실행). 2-B 완료 기준: JSON 응답 20회 호출, 형식 실패율 측정.
 * 실행: GEMINI_SMOKE=true ./gradlew test --tests "com.aidisciple.ai.gemini.GeminiSmokeTest" --rerun
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GEMINI_SMOKE", matches = "true")
class GeminiSmokeTest {

	private static final int TOTAL = 20;
	private static final long PAUSE_MILLIS = 4000;   // 무료 티어 분당 한도를 넘지 않게

	record Reply(String reply) {}

	@Autowired
	private LlmClient llmClient;

	@Autowired
	private AiProperties properties;

	@Test
	void 실제_Gemini를_20번_불러_JSON_형식_성공률을_잰다() throws InterruptedException {
		int success = 0;
		Map<String, Integer> failures = new TreeMap<>();

		for (int i = 1; i <= TOTAL; i++) {
			LlmRequest request = new LlmRequest(AiPurpose.TEACH_TURN, "smoke-test", "v1",
				Map.of("studentMessage", "스택은 나중에 넣은 것이 먼저 나오는 구조야. (" + i + "번째)"),
				null, "SMOKE", (long) i);
			try {
				Reply reply = llmClient.generateJson(request, Reply.class);
				if (reply.reply() != null && !reply.reply().isBlank()) {
					success++;
				} else {
					failures.merge("EMPTY_REPLY", 1, Integer::sum);
				}
			} catch (AiException e) {
				failures.merge(e.getErrorCode().name(), 1, Integer::sum);
			}
			Thread.sleep(PAUSE_MILLIS);
		}

		System.out.printf("[SMOKE] model=%s 성공 %d/%d (%.0f%%), 실패 %s%n",
			properties.gemini().model(), success, TOTAL, 100.0 * success / TOTAL, failures);
		assertThat(success).isPositive();
	}
}
