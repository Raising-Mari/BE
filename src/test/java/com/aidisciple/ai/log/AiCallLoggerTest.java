package com.aidisciple.ai.log;

import java.util.Map;

import com.aidisciple.ai.AiPurpose;
import com.aidisciple.ai.LlmRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiCallLoggerTest {

	private final AiCallLogRepository repository = mock(AiCallLogRepository.class);
	private final AiCallLogger logger =
		new AiCallLogger(repository, mock(PlatformTransactionManager.class));

	private final LlmRequest request = new LlmRequest(
		AiPurpose.TEACH_TURN, "teaching-turn", "v1", Map.of(), 1L, null, null);

	@Test
	void 저장하면_로그_id를_돌려준다() {
		AiCallLog saved = mock(AiCallLog.class);
		when(saved.getId()).thenReturn(7L);
		when(repository.saveAndFlush(any())).thenReturn(saved);

		assertThat(logger.record(AiCallRecord.success(request, AiProvider.GEMINI, "flash", 100, 50, 800)))
			.contains(7L);
	}

	@Test
	void 저장이_실패해도_예외를_던지지_않는다() {
		when(repository.saveAndFlush(any())).thenThrow(new RuntimeException("DB 연결 끊김"));

		assertThat(logger.record(AiCallRecord.failure(request, AiProvider.GEMINI, "flash", 300, "TIMEOUT")))
			.isEmpty();
	}

	@Test
	void 실패_기록은_토큰이_없고_오류코드는_40자로_자른다() {
		AiCallRecord record = AiCallRecord.failure(request, AiProvider.GEMINI, "flash", 300, "X".repeat(100));

		assertThat(record.success()).isFalse();
		assertThat(record.inputTokens()).isNull();
		assertThat(record.errorCode()).hasSize(40);
	}

	@Test
	void 요청의_프롬프트_이름과_버전이_기록에_들어간다() {
		AiCallRecord record = AiCallRecord.success(request, AiProvider.GEMINI, "flash", 100, 50, 800);

		assertThat(record.promptName()).isEqualTo("teaching-turn");
		assertThat(record.promptVersion()).isEqualTo("v1");
		assertThat(record.userId()).isEqualTo(1L);
	}
}
