package com.aidisciple.domain.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aidisciple.ai.AiPurpose;
import com.aidisciple.ai.LlmClient;

class DiscipleSolverTest {

	private final LlmClient llmClient = mock(LlmClient.class);
	private final DiscipleSolver solver = new DiscipleSolver(llmClient);

	private static final List<DiscipleSolver.SolveNote> NOTES = List.of(
		new DiscipleSolver.SolveNote(12, "스택은 나중에 넣은 것이 먼저 나온다."));
	private static final List<DiscipleSolver.SolveQuestion> QUESTIONS = List.of(
		new DiscipleSolver.SolveQuestion(1, "나중에 넣은 것이 먼저 나오는 자료구조는?"),
		new DiscipleSolver.SolveQuestion(2, "큐의 특징은?"),
		new DiscipleSolver.SolveQuestion(3, "트리의 정의는?"));

	private void aiAnswers(SolveLlmResponse.LlmAnswer... answers) {
		when(llmClient.generateJson(any(), eq(SolveLlmResponse.class))).thenReturn(new SolveLlmResponse(List.of(answers)));
	}

	@Test
	void 배틀에서는_노트를_인용하지_않은_답을_모름으로_바꾼다() {
		aiAnswers(
			new SolveLlmResponse.LlmAnswer(1L, "스택", false, List.of(12L)),
			new SolveLlmResponse.LlmAnswer(2L, "먼저 넣은 것이 먼저 나온다", false, List.of()),
			new SolveLlmResponse.LlmAnswer(3L, "", true, List.of()));

		List<DiscipleSolver.SolvedAnswer> answers = solver.solve(new DiscipleSolver.SolveInput(
			AiPurpose.BATTLE_SOLVE, 1L, "BATTLE", 9L, NOTES, QUESTIONS, true));

		assertThat(answers.get(0).unknown()).isFalse();
		assertThat(answers.get(0).usedNoteIds()).containsExactly(12L);
		assertThat(answers.get(1).unknown()).isTrue();
		assertThat(answers.get(1).forcedUnknown()).isTrue();
		assertThat(answers.get(2).unknown()).isTrue();
		assertThat(answers.get(2).forcedUnknown()).isFalse();
	}

	@Test
	void 주지_않은_노트_번호는_버린다() {
		aiAnswers(new SolveLlmResponse.LlmAnswer(1L, "스택", false, List.of(12L, 999L)));

		List<DiscipleSolver.SolvedAnswer> answers = solver.solve(new DiscipleSolver.SolveInput(
			AiPurpose.BATTLE_SOLVE, 1L, "BATTLE", 9L, NOTES, QUESTIONS.subList(0, 1), true));

		assertThat(answers.get(0).usedNoteIds()).containsExactly(12L);
	}

	@Test
	void AI가_빠뜨린_문항은_모름이다() {
		aiAnswers(new SolveLlmResponse.LlmAnswer(1L, "스택", false, List.of(12L)));

		List<DiscipleSolver.SolvedAnswer> answers = solver.solve(new DiscipleSolver.SolveInput(
			AiPurpose.BATTLE_SOLVE, 1L, "BATTLE", 9L, NOTES, QUESTIONS, true));

		assertThat(answers).hasSize(3);
		assertThat(answers.get(2).unknown()).isTrue();
	}

	@Test
	void 누출_시험에서는_노트_없이_답해도_모름으로_바꾸지_않는다() {
		aiAnswers(new SolveLlmResponse.LlmAnswer(1L, "스택", false, List.of()));

		List<DiscipleSolver.SolvedAnswer> answers = solver.solve(new DiscipleSolver.SolveInput(
			AiPurpose.LEAK_TEST, null, "QUESTION_JOB", 5L, List.of(), QUESTIONS.subList(0, 1), false));

		assertThat(answers.get(0).unknown()).isFalse();
		assertThat(answers.get(0).answer()).isEqualTo("스택");
	}

}
