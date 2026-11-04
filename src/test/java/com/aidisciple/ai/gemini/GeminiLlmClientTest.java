package com.aidisciple.ai.gemini;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.aidisciple.ai.AiException;
import com.aidisciple.ai.AiProperties;
import com.aidisciple.ai.AiPurpose;
import com.aidisciple.ai.LlmRequest;
import com.aidisciple.ai.gemini.GeminiApiException.Kind;
import com.aidisciple.ai.log.AiCallLogger;
import com.aidisciple.ai.log.AiCallRecord;
import com.aidisciple.ai.prompt.PromptLoader;
import com.aidisciple.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GeminiLlmClientTest {

	record Reply(String reply) {}

	private final FakeGeminiApi api = new FakeGeminiApi();
	private final AiCallLogger callLogger = mock(AiCallLogger.class);
	private final List<Long> sleeps = new ArrayList<>();

	private final AiProperties properties = new AiProperties(2,
		new AiProperties.Gemini("test-key", "test-model", 30, 1024, 3, 1000));

	private final GeminiLlmClient client = new GeminiLlmClient(
		api, new PromptLoader(), callLogger, JsonMapper.builder().build(), properties, sleeps::add);

	private final LlmRequest request = new LlmRequest(AiPurpose.TEACH_TURN, "sample-test", "v1",
		Map.of("studentMessage", "스택은 후입선출", "notes", "없음"), 1L, null, null);

	@Test
	void 정상_응답을_응답타입으로_바꾸고_성공을_기록한다() {
		api.then("{\"reply\":\"그럼 큐는요?\"}");

		Reply reply = client.generateJson(request, Reply.class);

		assertThat(reply.reply()).isEqualTo("그럼 큐는요?");
		assertThat(api.lastPrompt).contains("스택은 후입선출");
		assertThat(recorded()).singleElement()
			.satisfies(r -> {
				assertThat(r.success()).isTrue();
				assertThat(r.inputTokens()).isEqualTo(10);
			});
	}

	@Test
	void 서버오류는_백오프하며_재시도해서_성공한다() {
		api.then(new GeminiApiException(Kind.SERVER_ERROR, "500"))
			.then(new GeminiApiException(Kind.SERVER_ERROR, "503"))
			.then("{\"reply\":\"ok\"}");

		assertThat(client.generateJson(request, Reply.class).reply()).isEqualTo("ok");
		assertThat(api.calls).isEqualTo(3);
		assertThat(sleeps).containsExactly(1000L, 2000L);
	}

	@Test
	void 서버오류가_계속되면_AI_UNAVAILABLE() {
		api.then(new GeminiApiException(Kind.SERVER_ERROR, "500"))
			.then(new GeminiApiException(Kind.SERVER_ERROR, "500"))
			.then(new GeminiApiException(Kind.SERVER_ERROR, "500"));

		assertErrorCode(ErrorCode.AI_UNAVAILABLE);
		assertThat(api.calls).isEqualTo(3);
	}

	@Test
	void 타임아웃이_계속되면_AI_TIMEOUT() {
		api.then(new GeminiApiException(Kind.TIMEOUT, "t"))
			.then(new GeminiApiException(Kind.TIMEOUT, "t"))
			.then(new GeminiApiException(Kind.TIMEOUT, "t"));

		assertErrorCode(ErrorCode.AI_TIMEOUT);
	}

	@Test
	void 한도초과는_즉시_반복하지_않고_AI_RATE_LIMITED() {
		api.then(new GeminiApiException(Kind.RATE_LIMITED, "429"));

		assertErrorCode(ErrorCode.AI_RATE_LIMITED);
		assertThat(api.calls).isEqualTo(1);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void 키_오류같은_4xx는_재시도하지_않는다() {
		api.then(new GeminiApiException(Kind.CLIENT_ERROR, "403"));

		assertErrorCode(ErrorCode.AI_UNAVAILABLE);
		assertThat(api.calls).isEqualTo(1);
	}

	@Test
	void JSON이_한번_깨지면_다시_불러서_성공한다() {
		api.then("{깨진 json").then("{\"reply\":\"ok\"}");

		assertThat(client.generateJson(request, Reply.class).reply()).isEqualTo("ok");
		assertThat(api.calls).isEqualTo(2);
	}

	@Test
	void JSON이_두번_깨지면_AI_BAD_RESPONSE() {
		api.then("{깨진").then("또 깨진");

		assertErrorCode(ErrorCode.AI_BAD_RESPONSE);
		assertThat(api.calls).isEqualTo(2);
		assertThat(recorded()).allSatisfy(r -> assertThat(r.errorCode()).isEqualTo("BAD_RESPONSE"));
	}

	@Test
	void API키가_없으면_호출하지_않고_AI_UNAVAILABLE() {
		api.configured = false;

		assertErrorCode(ErrorCode.AI_UNAVAILABLE);
		assertThat(api.calls).isZero();
	}

	private void assertErrorCode(ErrorCode expected) {
		assertThatThrownBy(() -> client.generateJson(request, Reply.class))
			.isInstanceOf(AiException.class)
			.extracting(e -> ((AiException) e).getErrorCode())
			.isEqualTo(expected);
	}

	private List<AiCallRecord> recorded() {
		ArgumentCaptor<AiCallRecord> captor = ArgumentCaptor.forClass(AiCallRecord.class);
		verify(callLogger, times(api.calls)).record(captor.capture());
		return captor.getAllValues();
	}

	/** 미리 넣어 둔 응답(문자열) 또는 예외를 순서대로 돌려주는 가짜 Gemini */
	static class FakeGeminiApi implements GeminiApi {
		boolean configured = true;
		int calls;
		String lastPrompt;
		private final Deque<Object> results = new ArrayDeque<>();

		FakeGeminiApi then(Object result) {
			results.addLast(result);
			return this;
		}

		@Override
		public boolean isConfigured() {
			return configured;
		}

		@Override
		public GeminiRawResponse generate(String prompt, Class<?> responseType) {
			calls++;
			lastPrompt = prompt;
			Object next = results.pollFirst();
			if (next instanceof GeminiApiException e) {
				throw e;
			}
			return new GeminiRawResponse((String) next, 10, 5);
		}
	}
}
