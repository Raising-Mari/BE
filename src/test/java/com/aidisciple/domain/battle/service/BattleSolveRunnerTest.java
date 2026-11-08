package com.aidisciple.domain.battle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.aidisciple.ai.AiException;
import com.aidisciple.ai.AiPurpose;
import com.aidisciple.domain.battle.config.BattleProperties;
import com.aidisciple.domain.question.service.DiscipleSolver;
import com.aidisciple.domain.teaching.dto.NoteRef;
import com.aidisciple.global.error.ErrorCode;

class BattleSolveRunnerTest {

	private static final long PARTICIPANT_ID = 5L;
	private static final long BATTLE_ID = 50L;

	private final BattleSolveStore store = mock(BattleSolveStore.class);
	private final DiscipleSolver solver = mock(DiscipleSolver.class);
	private final BattleProperties properties = new BattleProperties(Duration.ofMinutes(30), 3, Duration.ZERO, Duration.ofMinutes(1));
	private final BattleSolveRunner runner = new BattleSolveRunner(store, solver, properties);

	private static BattleSolveStore.SolveContext context(List<NoteRef> notes) {
		return new BattleSolveStore.SolveContext(BATTLE_ID, PARTICIPANT_ID, 10L, notes,
			List.of(new DiscipleSolver.SolveQuestion(900L, "스택이란?"), new DiscipleSolver.SolveQuestion(901L, "큐란?")));
	}

	private static List<DiscipleSolver.SolvedAnswer> answers() {
		return List.of(new DiscipleSolver.SolvedAnswer(900L, "후입선출", false, List.of(7L), false),
			new DiscipleSolver.SolvedAnswer(901L, "", true, List.of(), false));
	}

	private void startsWith(BattleSolveStore.SolveContext context) {
		when(store.begin(PARTICIPANT_ID)).thenReturn(Optional.of(context));
	}

	@Test
	void 풀이를_선점하지_못하면_아무것도_하지_않는다() {
		when(store.begin(PARTICIPANT_ID)).thenReturn(Optional.empty());

		runner.run(PARTICIPANT_ID);

		verifyNoInteractions(solver);
		verify(store, never()).complete(anyLong(), anyList(), anyInt());
		verify(store, never()).fail(anyLong(), anyString(), anyInt());
	}

	@Test
	void 노트와_문항만_넣어_AI를_한_번_부르고_결과를_저장한다() {
		startsWith(context(List.of(new NoteRef(7L, "스택은 후입선출이다."))));
		when(solver.solve(any())).thenReturn(answers());

		runner.run(PARTICIPANT_ID);

		ArgumentCaptor<DiscipleSolver.SolveInput> input = ArgumentCaptor.forClass(DiscipleSolver.SolveInput.class);
		verify(solver, times(1)).solve(input.capture());
		assertThat(input.getValue().purpose()).isEqualTo(AiPurpose.BATTLE_SOLVE);
		assertThat(input.getValue().userId()).isEqualTo(10L);
		assertThat(input.getValue().relatedType()).isEqualTo("BATTLE");
		assertThat(input.getValue().relatedId()).isEqualTo(BATTLE_ID);
		assertThat(input.getValue().requireNoteCitation()).isTrue();
		assertThat(input.getValue().notes()).containsExactly(new DiscipleSolver.SolveNote(7L, "스택은 후입선출이다."));
		assertThat(input.getValue().questions()).extracting(DiscipleSolver.SolveQuestion::id).containsExactly(900L, 901L);
		verify(store).complete(PARTICIPANT_ID, answers(), 1);
		verify(store, never()).fail(anyLong(), anyString(), anyInt());
	}

	@Test
	void 노트가_없으면_AI를_부르지_않고_모두_모름으로_저장한다() {
		startsWith(context(List.of()));

		runner.run(PARTICIPANT_ID);

		verifyNoInteractions(solver);
		ArgumentCaptor<List<DiscipleSolver.SolvedAnswer>> saved = ArgumentCaptor.captor();
		verify(store).complete(eq(PARTICIPANT_ID), saved.capture(), eq(0));
		assertThat(saved.getValue()).extracting(DiscipleSolver.SolvedAnswer::unknown).containsExactly(true, true);
		assertThat(saved.getValue()).extracting(DiscipleSolver.SolvedAnswer::questionId).containsExactly(900L, 901L);
	}

	@Test
	void AI가_두_번_실패해도_세_번째에_성공하면_저장하고_시도_횟수를_남긴다() {
		startsWith(context(List.of(new NoteRef(7L, "노트"))));
		when(solver.solve(any()))
			.thenThrow(new AiException(ErrorCode.AI_TIMEOUT))
			.thenThrow(new AiException(ErrorCode.AI_BAD_RESPONSE))
			.thenReturn(answers());

		runner.run(PARTICIPANT_ID);

		verify(solver, times(3)).solve(any());
		verify(store).complete(PARTICIPANT_ID, answers(), 3);
		verify(store, never()).fail(anyLong(), anyString(), anyInt());
	}

	@Test
	void 세_번_모두_실패하면_오류_코드를_남기고_실패로_닫는다() {
		startsWith(context(List.of(new NoteRef(7L, "노트"))));
		when(solver.solve(any())).thenThrow(new AiException(ErrorCode.AI_UNAVAILABLE));

		runner.run(PARTICIPANT_ID);

		verify(solver, times(3)).solve(any());
		verify(store).fail(PARTICIPANT_ID, "AI_UNAVAILABLE", 3);
		verify(store, never()).complete(anyLong(), anyList(), anyInt());
	}

	@Test
	void AI_오류가_아닌_예외는_다시_시도하지_않고_예외_이름만_남긴다() {
		startsWith(context(List.of(new NoteRef(7L, "노트"))));
		when(solver.solve(any())).thenThrow(new IllegalArgumentException("노트 내용이 섞인 메시지"));

		runner.run(PARTICIPANT_ID);

		verify(solver, times(1)).solve(any());
		verify(store).fail(PARTICIPANT_ID, "IllegalArgumentException", 1);
	}

	@Test
	void 저장이_실패해도_배틀을_ERROR로_남긴다() {
		startsWith(context(List.of(new NoteRef(7L, "노트"))));
		when(solver.solve(any())).thenReturn(answers());
		org.mockito.Mockito.doThrow(new IllegalStateException("db")).when(store).complete(anyLong(), anyList(), anyInt());

		runner.run(PARTICIPANT_ID);

		verify(store).fail(PARTICIPANT_ID, "IllegalStateException", 1);
	}

}
