package com.aidisciple.domain.question.logic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

class CodeGraderTest {

	private static final List<KeyElement> STACK_ELEMENTS = List.of(
		new KeyElement("후입선출", List.of("LIFO")),
		new KeyElement("push", List.of("삽입")),
		new KeyElement("pop", List.of("꺼내기")));

	@Test
	void 정답_표현을_포함하면_정답이다() {
		assertThat(CodeGrader.gradeAnswerList("그건 스택이에요", List.of("스택", "stack")))
			.isEqualTo(CodeVerdict.CORRECT);
		assertThat(CodeGrader.gradeAnswerList("STACK 입니다", List.of("스택", "stack")))
			.isEqualTo(CodeVerdict.CORRECT);
	}

	@Test
	void 공통_핵심어가_없으면_오답이다() {
		assertThat(CodeGrader.gradeAnswerList("큐입니다", List.of("스택 자료구조"))).isEqualTo(CodeVerdict.WRONG);
	}

	@Test
	void 핵심어만_겹치면_애매하다() {
		assertThat(CodeGrader.gradeAnswerList("자료구조의 한 종류", List.of("스택 자료구조")))
			.isEqualTo(CodeVerdict.AMBIGUOUS);
	}

	@Test
	void 빈_답은_오답이다() {
		assertThat(CodeGrader.gradeAnswerList("  ", List.of("스택"))).isEqualTo(CodeVerdict.WRONG);
	}

	@Test
	void 핵심_요소는_비율로_가른다() {
		assertThat(CodeGrader.gradeKeyElements("LIFO 구조라 삽입하고 pop 한다", STACK_ELEMENTS, null))
			.isEqualTo(CodeVerdict.CORRECT);
		assertThat(CodeGrader.gradeKeyElements("LIFO 구조다", STACK_ELEMENTS, null))
			.isEqualTo(CodeVerdict.AMBIGUOUS);
		assertThat(CodeGrader.gradeKeyElements("잘 모르겠다", STACK_ELEMENTS, null))
			.isEqualTo(CodeVerdict.WRONG);
	}

	@Test
	void 노트를_주면_노트에도_있는_요소만_센다() {
		double ratio = CodeGrader.keyElementRatio("LIFO 구조라 삽입하고 pop 한다", STACK_ELEMENTS, "스택은 LIFO 구조야");
		assertThat(ratio).isCloseTo(1.0 / 3, within(0.0001));
	}

}
