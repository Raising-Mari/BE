package com.aidisciple.domain.question.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aidisciple.domain.question.entity.GradingMode;

class EvidenceCheckerTest {

	private static final Map<Integer, SourceSentence> SENTENCES = Map.of(
		1, new SourceSentence(1, 101L, "스택은 나중에 넣은 데이터가 먼저 나오는 후입선출 구조이다."),
		2, new SourceSentence(2, 102L, "큐는 먼저 넣은 데이터가 먼저 나오는 선입선출 구조이다."));

	private static QuestionCandidate answerList(List<String> answers, String quote, List<Integer> nos) {
		return new QuestionCandidate("나중에 넣은 데이터가 먼저 나오는 자료구조는?", GradingMode.ANSWER_LIST, answers, null,
			"스택", quote, nos);
	}

	@Test
	void 근거와_정답이_자료에_있으면_통과한다() {
		QuestionCandidate candidate = answerList(List.of("스택"), "나중에 넣은 데이터가 먼저 나오는 후입선출 구조", List.of(1));
		assertThat(EvidenceChecker.check(candidate, SENTENCES, List.of())).isEmpty();
	}

	@Test
	void 없는_문장_번호를_인용하면_탈락한다() {
		QuestionCandidate candidate = answerList(List.of("스택"), "나중에 넣은 데이터가 먼저 나오는 후입선출 구조", List.of(9));
		assertThat(EvidenceChecker.check(candidate, SENTENCES, List.of()))
			.containsExactly(CandidateFailure.EVIDENCE_NOT_FOUND);
	}

	@Test
	void 인용이_문장에_없으면_탈락한다() {
		QuestionCandidate candidate = answerList(List.of("스택"), "스택은 배열로만 구현한다", List.of(1));
		assertThat(EvidenceChecker.check(candidate, SENTENCES, List.of()))
			.containsExactly(CandidateFailure.EVIDENCE_NOT_FOUND);
	}

	@Test
	void 정답이_근거_문장에_없으면_탈락한다() {
		QuestionCandidate candidate = answerList(List.of("트리"), "나중에 넣은 데이터가 먼저 나오는 후입선출 구조", List.of(1));
		assertThat(EvidenceChecker.check(candidate, SENTENCES, List.of()))
			.containsExactly(CandidateFailure.ANSWER_NOT_IN_EVIDENCE);
	}

	@Test
	void 기존_문항과_비슷하면_중복이다() {
		QuestionCandidate candidate = answerList(List.of("스택"), "나중에 넣은 데이터가 먼저 나오는 후입선출 구조", List.of(1));
		assertThat(EvidenceChecker.check(candidate, SENTENCES, List.of("나중에 넣은 데이터가 먼저 나오는 자료구조는 무엇인가?")))
			.containsExactly(CandidateFailure.DUPLICATE);
	}

	@Test
	void 핵심_요소형은_요소가_근거에_있어야_한다() {
		QuestionCandidate candidate = new QuestionCandidate("스택의 특징을 설명하시오.", GradingMode.KEY_ELEMENTS, null,
			List.of(new KeyElement("후입선출", List.of("LIFO"))), "나중에 넣은 것이 먼저 나온다.",
			"나중에 넣은 데이터가 먼저 나오는 후입선출 구조", List.of(1));
		assertThat(EvidenceChecker.check(candidate, SENTENCES, List.of())).isEmpty();
	}

	@Test
	void 정답이_비어_있으면_형식_오류다() {
		QuestionCandidate candidate = answerList(List.of(), "나중에 넣은 데이터가 먼저 나오는 후입선출 구조", List.of(1));
		assertThat(EvidenceChecker.check(candidate, SENTENCES, List.of()))
			.containsExactly(CandidateFailure.INVALID_FORMAT);
	}

}
