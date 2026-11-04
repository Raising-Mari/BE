package com.aidisciple.global.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CharNgramsTest {

	@Test
	void 메시지에_그대로_있는_노트는_포함률_1() {
		assertThat(CharNgrams.containment("스택은 후입선출", "음 스택은 후입선출이야", 3)).isEqualTo(1.0);
	}

	@Test
	void 공백과_문장부호는_무시한다() {
		assertThat(CharNgrams.containment("스택은, 후입선출!", "스택은후입선출", 3)).isEqualTo(1.0);
	}

	@Test
	void 일부만_겹치면_겹친_비율() {
		// 가나다라 → {가나다, 나다라}, 가나다마 → {가나다, 나다마} → 1/2
		assertThat(CharNgrams.containment("가나다라", "가나다마", 3)).isEqualTo(0.5);
	}

	@Test
	void n보다_짧은_source는_통째로_들어있는지로_본다() {
		assertThat(CharNgrams.containment("스택", "자료구조 스택", 3)).isEqualTo(1.0);
		assertThat(CharNgrams.containment("큐", "자료구조 스택", 3)).isEqualTo(0.0);
	}

	@Test
	void 빈_source는_0() {
		assertThat(CharNgrams.containment("", "스택", 3)).isEqualTo(0.0);
	}

	@Test
	void 같은_문장은_유사도_1() {
		assertThat(CharNgrams.similarity("스택은 나중에 넣은 것이 먼저 나온다",
			"스택은 나중에 넣은 것이 먼저 나온다.", 3)).isEqualTo(1.0);
	}

	@Test
	void 유사도는_순서와_상관없다() {
		String a = "스택은 후입선출이다";
		String b = "큐는 선입선출이다";
		assertThat(CharNgrams.similarity(a, b, 3)).isEqualTo(CharNgrams.similarity(b, a, 3));
	}

	@Test
	void 유사도는_Dice_계수로_계산한다() {
		// 스택은후입선출이다 → 7개, 큐는선입선출이다 → 6개, 공통 {입선출, 선출이, 출이다} 3개 → 2*3/13
		assertThat(CharNgrams.similarity("스택은 후입선출이다", "큐는 선입선출이다", 3))
			.isCloseTo(6.0 / 13, within(1e-9));
	}
}
