package com.aidisciple.domain.material.logic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextSanitizerTest {

	@Test
	void NUL_같은_제어문자는_지우고_줄바꿈과_탭은_남긴다() {
		assertThat(TextSanitizer.clean("스택\u0000은\u0001 구조\u007f이다\n다음\t줄\r\n")).isEqualTo("스택은 구조이다\n다음\t줄\r\n");
	}

	@Test
	void 짝이_맞지_않는_서러게이트는_지운다() {
		String lonelyHigh = "앞" + '\uD83D' + "뒤";
		String lonelyLow = "앞" + '\uDE00' + "뒤";

		assertThat(TextSanitizer.clean(lonelyHigh)).isEqualTo("앞뒤");
		assertThat(TextSanitizer.clean(lonelyLow)).isEqualTo("앞뒤");
	}

	@Test
	void 짝이_맞는_이모지와_일반_글은_그대로_둔다() {
		assertThat(TextSanitizer.clean("스택 😀 Stack 123 가나다.")).isEqualTo("스택 😀 Stack 123 가나다.");
	}

	@Test
	void null은_빈_문자열이다() {
		assertThat(TextSanitizer.clean(null)).isEmpty();
		assertThat(TextSanitizer.clean("")).isEmpty();
	}

}
