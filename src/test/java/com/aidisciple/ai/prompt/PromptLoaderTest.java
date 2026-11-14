package com.aidisciple.ai.prompt;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLoaderTest {

	private final PromptLoader loader = new PromptLoader();

	@Test
	void 이름과_버전으로_파일을_읽는다() {
		PromptTemplate template = loader.load("sample-test", "v1");

		assertThat(template.name()).isEqualTo("sample-test");
		assertThat(template.version()).isEqualTo("v1");
		assertThat(template.variableNames()).containsExactly("studentMessage", "notes");
	}

	@Test
	void 읽은_프롬프트로_변수를_채울수_있다() {
		String text = loader.load("sample-test", "v1")
			.render(Map.of("studentMessage", "큐는 선입선출", "notes", "없음"));

		assertThat(text).contains("큐는 선입선출");
	}

	@Test
	void 같은_프롬프트는_캐시된_객체를_돌려준다() {
		assertThat(loader.load("sample-test", "v1")).isSameAs(loader.load("sample-test", "v1"));
	}

	@Test
	void 파일이_없으면_예외() {
		assertThatThrownBy(() -> loader.load("no-such-prompt", "v1"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 경로를_벗어나는_이름은_거부한다() {
		assertThatThrownBy(() -> loader.load("../application", "v1"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 버전_형식이_틀리면_거부한다() {
		assertThatThrownBy(() -> loader.load("sample-test", "1"))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
