package com.aidisciple.domain.battle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aidisciple.domain.battle.dto.ReadyRequest;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class BattleReadyServiceTest {

	private final BattleStartStore store = mock(BattleStartStore.class);
	private final BattleSolveDispatcher dispatcher = mock(BattleSolveDispatcher.class);
	private final QuestionBankService questionBankService = mock(QuestionBankService.class);
	private final BattleReadyService service = new BattleReadyService(store, dispatcher, questionBankService);

	@Test
	void 시작되면_저장이_끝난_뒤_참가자마다_풀이를_접수한다() {
		when(store.markReady(10L, 50L, true)).thenReturn(
			new BattleStartStore.ReadyOutcome(BattleStartStore.ReadyResult.STARTED, List.of(1L, 2L), 2L));

		service.ready(10L, 50L, new ReadyRequest(true));

		verify(dispatcher).submitAll(List.of(1L, 2L));
	}

	@Test
	void 본문을_생략하면_공개_여부를_넘기지_않는다() {
		when(store.markReady(10L, 50L, null)).thenReturn(
			new BattleStartStore.ReadyOutcome(BattleStartStore.ReadyResult.WAITING_FOR_OTHER, List.of(), 2L));

		service.ready(10L, 50L, null);

		verify(store).markReady(10L, 50L, null);
		verifyNoInteractions(dispatcher, questionBankService);
	}

	@Test
	void 상대를_기다리거나_이미_준비했으면_접수하지_않는다() {
		for (BattleStartStore.ReadyResult result : List.of(BattleStartStore.ReadyResult.WAITING_FOR_OTHER,
			BattleStartStore.ReadyResult.ALREADY_READY)) {
			when(store.markReady(10L, 50L, null)).thenReturn(new BattleStartStore.ReadyOutcome(result, List.of(), 2L));

			service.ready(10L, 50L, new ReadyRequest(null));
		}

		verifyNoInteractions(dispatcher, questionBankService);
	}

	@Test
	void 문항이_모자라면_저장이_끝난_뒤_재고_보충을_걸고_오류를_던진다() {
		when(store.markReady(10L, 50L, null)).thenReturn(
			new BattleStartStore.ReadyOutcome(BattleStartStore.ReadyResult.NOT_ENOUGH_QUESTIONS, List.of(), 2L));

		Throwable thrown = catchThrowable(() -> service.ready(10L, 50L, null));

		assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BATTLE_NOT_ENOUGH_QUESTIONS));
		verify(questionBankService).requestRestock(2L);
		verify(dispatcher, never()).submitAll(anyCollection());
	}

	@Test
	void 재시도하면_다시_접수해야_할_참가자만_접수한다() {
		when(store.retrySolve(10L, 50L)).thenReturn(List.of(1L));

		service.retrySolve(10L, 50L);

		verify(dispatcher).submitAll(List.of(1L));
	}

	@Test
	void 재시도가_거부되면_아무것도_접수하지_않는다() {
		when(store.retrySolve(10L, 50L)).thenThrow(new BusinessException(ErrorCode.CONFLICT));

		Throwable thrown = catchThrowable(() -> service.retrySolve(10L, 50L));

		assertThat(thrown).isInstanceOf(BusinessException.class);
		verify(dispatcher, never()).submitAll(anyCollection());
		verify(dispatcher, never()).submit(anyLong());
	}

}
