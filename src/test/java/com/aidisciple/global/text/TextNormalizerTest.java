package com.aidisciple.global.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

	@Test
	void null과_빈문자열은_빈문자열() {
		assertThat(TextNormalizer.normalize(null)).isEmpty();
		assertThat(TextNormalizer.normalize("   ")).isEmpty();
	}

	@Test
	void 문장부호는_공백으로_바뀌고_연속공백은_하나로() {
		assertThat(TextNormalizer.normalize("스택은,   나중에 넣은 것이!! 먼저 나온다."))
			.isEqualTo("스택은 나중에 넣은 것이 먼저 나온다");
	}

	@Test
	void 전각문자와_대문자를_정리한다() {
		assertThat(TextNormalizer.normalize("ＬＩＦＯ Stack")).isEqualTo("lifo stack");
	}

	@Test
	void 제로폭공백과_줄바꿈없는공백을_정리한다() {
		assertThat(TextNormalizer.normalize("스\u200B택\u00A0구조")).isEqualTo("스택 구조");
	}

	@Test
	void compact는_공백까지_없앤다() {
		assertThat(TextNormalizer.compact("스택은 나중에"))
			.isEqualTo(TextNormalizer.compact("스택은나중에"));
	}
}
