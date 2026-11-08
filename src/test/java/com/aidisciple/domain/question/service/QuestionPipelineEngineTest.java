package com.aidisciple.domain.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aidisciple.ai.LlmClient;
import com.aidisciple.domain.question.config.QuestionProperties;
import com.aidisciple.domain.question.entity.ValidationStage;
import com.aidisciple.domain.question.logic.CandidateFailure;
import com.aidisciple.domain.question.logic.SourceSentence;
import com.aidisciple.domain.question.service.QuestionGenerateResponse.GeneratedQuestion;

class QuestionPipelineEngineTest {

	private static final List<SourceSentence> SENTENCES = List.of(
		new SourceSentence(1, 101L, "스택은 나중에 넣은 데이터가 먼저 나오는 후입선출 구조이다."),
		new SourceSentence(2, 102L, "큐는 먼저 넣은 데이터가 먼저 나오는 선입선출 구조이다."));

	private final LlmClient llmClient = mock(LlmClient.class);
	private final QuestionPipelineEngine engine = new QuestionPipelineEngine(llmClient, new DiscipleSolver(llmClient),
		QuestionProperties.defaults());

	private static GeneratedQuestion question(String stem, String answer, String quote, int no) {
		return new GeneratedQuestion(stem, "ANSWER_LIST", List.of(answer), List.of(), answer, quote, List.of(no));
	}

	private QuestionPipelineEngine.RoundResult run() {
		return engine.runRound(new QuestionPipelineEngine.RoundInput(5L, "3주차", SENTENCES, List.of(), 0));
	}

	@Test
	void 근거가_없는_후보는_코드_확인에서_떨어지고_노트_없이_맞힌_후보는_누출로_떨어진다() {
		when(llmClient.generateJson(any(), eq(QuestionGenerateResponse.class))).thenReturn(
			new QuestionGenerateResponse(List.of(
				question("나중에 넣은 데이터가 먼저 나오는 구조는?", "스택", "나중에 넣은 데이터가 먼저 나오는 후입선출 구조", 1),
				question("자료에 없는 내용을 묻는 문항입니다?", "트리", "트리는 계층 구조이다", 1),
				question("선입선출 방식으로 동작하는 자료구조의 이름은?", "큐", "먼저 넣은 데이터가 먼저 나오는 선입선출 구조", 2))));		// 누출 시험: 1번(스택)은 모른다고 하고, 2번(큐)은 노트 없이 맞힌다
		when(llmClient.generateJson(any(), eq(SolveLlmResponse.class))).thenReturn(new SolveLlmResponse(List.of(
			new SolveLlmResponse.LlmAnswer(1L, "", true, List.of()),
			new SolveLlmResponse.LlmAnswer(2L, "큐입니다", false, List.of()))));

		QuestionPipelineEngine.RoundResult result = run();

		assertThat(result.generatedCount()).isEqualTo(3);
		assertThat(result.passed()).extracting(o -> o.candidate().stem())
			.containsExactly("나중에 넣은 데이터가 먼저 나오는 구조는?");
		assertThat(result.outcomes()).filteredOn(o -> !o.passed())
			.extracting(QuestionPipelineEngine.CandidateOutcome::failedStage)
			.containsExactlyInAnyOrder(ValidationStage.EVIDENCE_CHECK, ValidationStage.LEAK_TEST);
		assertThat(result.outcomes()).filteredOn(o -> o.failedStage() == ValidationStage.LEAK_TEST)
			.flatExtracting(QuestionPipelineEngine.CandidateOutcome::failures)
			.containsExactly(CandidateFailure.LEAKED);
	}

	@Test
	void 코드_확인을_모두_떨어지면_누출_시험을_부르지_않는다() {
		when(llmClient.generateJson(any(), eq(QuestionGenerateResponse.class))).thenReturn(
			new QuestionGenerateResponse(List.of(question("자료에 없는 내용을 묻는 문항입니다?", "트리", "트리는 계층 구조이다", 1))));

		QuestionPipelineEngine.RoundResult result = run();

		assertThat(result.passed()).isEmpty();
		verify(llmClient, never()).generateJson(any(), eq(SolveLlmResponse.class));
	}

	@Test
	void 모르는_채점_방식은_형식_오류로_떨어진다() {
		when(llmClient.generateJson(any(), eq(QuestionGenerateResponse.class))).thenReturn(
			new QuestionGenerateResponse(List.of(new GeneratedQuestion("나중에 넣은 데이터가 먼저 나오는 구조는?", "MULTIPLE_CHOICE",
				List.of("스택"), List.of(), "스택", "나중에 넣은 데이터가 먼저 나오는 후입선출 구조", List.of(1)))));

		QuestionPipelineEngine.RoundResult result = run();

		assertThat(result.outcomes()).singleElement()
			.extracting(QuestionPipelineEngine.CandidateOutcome::failures)
			.isEqualTo(List.of(CandidateFailure.INVALID_FORMAT));
	}

}
