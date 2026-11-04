package com.aidisciple.global.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NegationDetectorTest {

	@Test
	void 긍정문은_0개() {
		assertThat(NegationDetector.count("스택은 후입선출이다")).isZero();
	}

	@Test
	void 아니다는_1개() {
		assertThat(NegationDetector.count("스택은 선입선출이 아니다")).isEqualTo(1);
	}

	@Test
	void 부정이_뒤집히면_홀짝이_다르다() {
		assertThat(NegationDetector.sameParity("큐는 선입선출이다", "큐는 선입선출이 아니다")).isFalse();
	}

	@Test
	void 이중부정은_짝수라_긍정과_홀짝이_같다() {
		assertThat(NegationDetector.count("쓸모없지 않다")).isEqualTo(2);
		assertThat(NegationDetector.sameParity("쓸모 있다", "쓸모없지 않다")).isTrue();
	}

	@Test
	void X는_독립된_글자일_때만_센다() {
		assertThat(NegationDetector.count("정답 X")).isEqualTo(1);
		assertThat(NegationDetector.count("index 구조")).isZero();
	}

	@Test
	void 알려진_한계_안정의_안도_센다() {
		assertThat(NegationDetector.count("스택은 안정적이다")).isEqualTo(1);
	}

	@Test
	void null은_0개() {
		assertThat(NegationDetector.count(null)).isZero();
	}
}
