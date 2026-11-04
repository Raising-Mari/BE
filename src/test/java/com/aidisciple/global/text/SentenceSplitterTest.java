package com.aidisciple.global.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentenceSplitterTest {

	@Test
	void 마침표_물음표_느낌표로_나눈다() {
		assertThat(SentenceSplitter.split("스택은 후입선출이다. 큐는 무엇인가? 정말 중요하다!"))
			.containsExactly("스택은 후입선출이다.", "큐는 무엇인가?", "정말 중요하다!");
	}

	@Test
	void 줄바꿈은_문장_경계다() {
		assertThat(SentenceSplitter.split("자료구조\n큐는 선입선출"))
			.containsExactly("자료구조", "큐는 선입선출");
	}

	@Test
	void 소수점에서는_나누지_않는다() {
		assertThat(SentenceSplitter.split("원주율은 3.14이다. 다음 내용이다."))
			.containsExactly("원주율은 3.14이다.", "다음 내용이다.");
	}

	@Test
	void 번호_목록은_다음_내용과_붙인다() {
		assertThat(SentenceSplitter.split("1. 스택은 후입선출이다."))
			.containsExactly("1. 스택은 후입선출이다.");
	}

	@Test
	void 닫는_따옴표까지_한_문장이다() {
		assertThat(SentenceSplitter.split("\"스택이다.\" 다음 문장이다."))
			.containsExactly("\"스택이다.\"", "다음 문장이다.");
	}

	@Test
	void 빈줄과_기호만_있는_줄은_버린다() {
		assertThat(SentenceSplitter.split("   \n•\n---\n\r\n스택이다."))
			.containsExactly("스택이다.");
	}

	@Test
	void null이면_빈목록() {
		assertThat(SentenceSplitter.split(null)).isEmpty();
	}
}
