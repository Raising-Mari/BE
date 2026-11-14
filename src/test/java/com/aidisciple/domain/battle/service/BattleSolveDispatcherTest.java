package com.aidisciple.domain.battle.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

class BattleSolveDispatcherTest {

	private final BattleSolveExecutor executor = mock(BattleSolveExecutor.class);
	private final BattleSolveStore store = mock(BattleSolveStore.class);
	private final BattleSolveDispatcher dispatcher = new BattleSolveDispatcher(executor, store);

	@Test
	void 참가자마다_풀이를_접수한다() {
		dispatcher.submitAll(List.of(5L, 6L));

		verify(executor).runAsync(5L);
		verify(executor).runAsync(6L);
		verify(store, never()).fail(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyInt());
	}

	@Test
	void 큐가_가득_차면_조용히_버리지_않고_실패로_닫는다() {
		doThrow(new TaskRejectedException("full")).when(executor).runAsync(5L);

		dispatcher.submit(5L);

		verify(store).fail(5L, "QUEUE_FULL", 0);
	}

}
