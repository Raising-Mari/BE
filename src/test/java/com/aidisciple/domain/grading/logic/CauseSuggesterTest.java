package com.aidisciple.domain.grading.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.aidisciple.domain.question.dto.GradingQuestion;
import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.logic.KeyElement;
import com.aidisciple.domain.review.entity.ReviewCause;

class CauseSuggesterTest {

	private static final String EVIDENCE = "스택은 가장 나중에 들어간 자료가 가장 먼저 나오는 후입선출 구조이다.";

	private final CauseSuggester suggester = new CauseSuggester(0.25);

	private static GradingQuestion answerList() {
		return new GradingQuestion(1L, "스택의 입출력 순서는?", GradingMode.ANSWER_LIST, List.of("후입선출", "LIFO"), null,
			"후입선출", EVIDENCE);
	}

	private static GradingQuestion keyElements() {
		return new GradingQuestion(2L, "스택을 설명하라", GradingMode.KEY_ELEMENTS, null,
			List.of(new KeyElement("후입선출", List.of("lifo")), new KeyElement("가장 나중", List.of())),
			"후입선출, 가장 나중에 넣은 것이 먼저", EVIDENCE);
	}

	private ReviewCause suggest(GradingQuestion question, List<String> notes, List<String> messages) {
		return suggester.suggest(question, notes, () -> messages);
	}

	// --- 근거와 겹치는 노트가 없다: 가르쳤는가 ---

	@Test
	void 대화에도_노트에도_없으면_NOT_TAUGHT다() {
		ReviewCause cause = suggest(answerList(), List.of("큐는 먼저 넣은 것이 먼저 나온다."),
			List.of("큐는 먼저 넣은 것이 먼저 나온다.", "오늘은 운영체제 이야기를 하자"));

		assertThat(cause).isEqualTo(ReviewCause.NOT_TAUGHT);
	}

	@Test
	void 대화에는_있는데_노트로_안_남았으면_NOT_IN_NOTES다() {
		ReviewCause cause = suggest(answerList(), List.of("큐는 먼저 넣은 것이 먼저 나온다."),
			List.of("스택은 가장 나중에 들어간 자료가 가장 먼저 나오는 구조야", "큐는 먼저 넣은 것이 먼저 나온다."));

		assertThat(cause).isEqualTo(ReviewCause.NOT_IN_NOTES);
	}

	@Test
	void 노트가_하나도_없어도_대화로_가르쳤는지에_따라_가른다() {
		assertThat(suggest(answerList(), List.of(), List.of("스택은 가장 나중에 들어간 자료가 가장 먼저 나오는 구조야")))
			.isEqualTo(ReviewCause.NOT_IN_NOTES);
		assertThat(suggest(answerList(), List.of(), List.of())).isEqualTo(ReviewCause.NOT_TAUGHT);
	}

	@Test
	void 학생_메시지는_겹치는_노트가_없을_때만_읽는다() {
		AtomicInteger reads = new AtomicInteger();

		suggester.suggest(answerList(), List.of(EVIDENCE), () -> {
			reads.incrementAndGet();
			return List.of();
		});
		assertThat(reads).hasValue(0);

		suggester.suggest(answerList(), List.of("전혀 다른 노트"), () -> {
			reads.incrementAndGet();
			return List.of();
		});
		assertThat(reads).hasValue(1);
	}

	// --- 겹치는 노트가 있다: 노트가 틀렸는가, 제자가 못 썼는가 ---

	@Test
	void 겹치는_노트가_있지만_정답_표현이_빠졌으면_NOTE_WRONG이다() {
		ReviewCause cause = suggest(answerList(), List.of("스택은 가장 나중에 들어간 자료가 가장 먼저 나오는 구조이다."), List.of());

		assertThat(cause).isEqualTo(ReviewCause.NOTE_WRONG);
	}

	@Test
	void 노트의_부정_표현이_근거와_반대면_정답_표현이_있어도_NOTE_WRONG이다() {
		ReviewCause cause = suggest(answerList(),
			List.of("스택은 가장 나중에 들어간 자료가 가장 먼저 나오지 않는 후입선출 구조이다."), List.of());

		assertThat(cause).isEqualTo(ReviewCause.NOTE_WRONG);
	}

	@Test
	void 핵심_요소형은_요소가_하나라도_노트에_없으면_NOTE_WRONG이다() {
		ReviewCause cause = suggest(keyElements(), List.of("스택은 가장 나중에 들어간 자료가 가장 먼저 나오는 구조이다."), List.of());

		assertThat(cause).isEqualTo(ReviewCause.NOTE_WRONG);
	}

	@Test
	void 핵심_요소가_노트에_모두_있는데도_틀렸으면_NOT_APPLIED다() {
		assertThat(suggest(answerList(), List.of(EVIDENCE), List.of())).isEqualTo(ReviewCause.NOT_APPLIED);
		assertThat(suggest(keyElements(), List.of("스택은 가장 나중에 들어간 자료가 가장 먼저 나오는 후입선출(LIFO) 구조이다."), List.of()))
			.isEqualTo(ReviewCause.NOT_APPLIED);
	}

	@Test
	void 핵심_요소는_여러_노트에_나뉘어_있어도_모두_있으면_갖춰진_것으로_본다() {
		ReviewCause cause = suggest(keyElements(), List.of(
			"스택은 가장 나중에 들어간 자료가 가장 먼저 나오는 구조이다.", "스택은 후입선출 이라고 부른다"), List.of());

		assertThat(cause).isEqualTo(ReviewCause.NOT_APPLIED);
	}

	@Test
	void 기타는_제안하지_않는다() {
		for (List<String> notes : List.of(List.<String>of(), List.of("다른 이야기"), List.of(EVIDENCE))) {
			assertThat(suggest(answerList(), notes, List.of())).isNotEqualTo(ReviewCause.OTHER);
		}
	}

	@Test
	void 겹침_기준은_설정으로_바꿀_수_있다() {
		String loose = "스택은 가장 나중에 넣은 것이 먼저 나온다";

		assertThat(new CauseSuggester(0.1).suggest(answerList(), List.of(loose), () -> List.of()))
			.isNotEqualTo(ReviewCause.NOT_TAUGHT);
		assertThat(new CauseSuggester(0.95).suggest(answerList(), List.of(loose), () -> List.of()))
			.isEqualTo(ReviewCause.NOT_TAUGHT);
	}

}
