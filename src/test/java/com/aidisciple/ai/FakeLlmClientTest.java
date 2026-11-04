package com.aidisciple.ai;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeLlmClientTest {

	record SampleResponse(String reply) {}

	private LlmRequest request() {
		return new LlmRequest(AiPurpose.TEACH_TURN, "teaching-turn", "v1",
			Map.of(), 1L, null, null);
	}

	@Test
	void 넣어둔_응답을_순서대로_돌려준다() {
		FakeLlmClient llm = new FakeLlmClient()
			.willReturn(new SampleResponse("첫번째"))
			.willReturn(new SampleResponse("두번째"));

		assertThat(llm.generateJson(request(), SampleResponse.class).reply()).isEqualTo("첫번째");
		assertThat(llm.generateJson(request(), SampleResponse.class).reply()).isEqualTo("두번째");
		assertThat(llm.callCount()).isEqualTo(2);
	}

	@Test
	void 넣어둔_예외를_던진다() {
		FakeLlmClient llm = new FakeLlmClient()
			.willThrow(new IllegalStateException("AI 장애"));

		assertThatThrownBy(() -> llm.generateJson(request(), SampleResponse.class))
			.hasMessage("AI 장애");
	}

	@Test
	void 응답이_없으면_알려준다() {
		assertThatThrownBy(() -> new FakeLlmClient().generateJson(request(), SampleResponse.class))
			.isInstanceOf(IllegalStateException.class);
	}
}
