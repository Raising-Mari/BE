package com.aidisciple.domain.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.aidisciple.domain.battle.service.BattleRecordService;
import com.aidisciple.domain.ranking.entity.EloHistory;
import com.aidisciple.domain.ranking.repository.EloHistoryRepository;

class EloRecoveryTest {

	private final BattleRecordService battleRecordService = mock(BattleRecordService.class);
	private final EloHistoryRepository historyRepository = mock(EloHistoryRepository.class);
	private final EloService eloService = mock(EloService.class);
	private final EloRecovery recovery = new EloRecovery(battleRecordService, historyRepository, eloService,
		mock(PlatformTransactionManager.class));

	private static EloHistory historyOf(long battleId) {
		return EloHistory.of(10L, 1L, battleId, 1000, 1016);
	}

	@Test
	void Elo가_빠진_배틀만_끝난_순서대로_반영한다() {
		when(battleRecordService.finishedRankedBattleIds()).thenReturn(List.of(1L, 2L, 3L));
		when(historyRepository.findByBattleIdIn(anyCollection())).thenReturn(List.of(historyOf(2L)));
		when(eloService.apply(anyLong())).thenReturn(true);

		int applied = recovery.repair();

		assertThat(applied).isEqualTo(2);
		org.mockito.InOrder order = org.mockito.Mockito.inOrder(eloService);
		order.verify(eloService).apply(1L);
		order.verify(eloService).apply(3L);
		verify(eloService, never()).apply(2L);
	}

	@Test
	void 모두_반영돼_있으면_아무것도_하지_않는다() {
		when(battleRecordService.finishedRankedBattleIds()).thenReturn(List.of(1L));
		when(historyRepository.findByBattleIdIn(anyCollection())).thenReturn(List.of(historyOf(1L)));

		assertThat(recovery.repair()).isZero();
		verify(eloService, never()).apply(anyLong());
	}

	@Test
	void 끝난_랭킹_배틀이_없으면_이력을_조회하지도_않는다() {
		when(battleRecordService.finishedRankedBattleIds()).thenReturn(List.of());

		assertThat(recovery.repair()).isZero();
		verify(historyRepository, never()).findByBattleIdIn(anyCollection());
	}

	@Test
	void 한_배틀의_실패가_다른_배틀을_막지_않는다() {
		when(battleRecordService.finishedRankedBattleIds()).thenReturn(List.of(1L, 2L, 3L));
		when(historyRepository.findByBattleIdIn(anyCollection())).thenReturn(List.of());
		when(eloService.apply(1L)).thenReturn(true);
		when(eloService.apply(2L)).thenThrow(new IllegalStateException("db"));
		when(eloService.apply(3L)).thenReturn(true);

		int applied = recovery.repair();

		assertThat(applied).isEqualTo(2);
		verify(eloService).apply(3L);
	}

	@Test
	void 반영할_수_없는_배틀은_세지_않는다() {
		when(battleRecordService.finishedRankedBattleIds()).thenReturn(List.of(1L));
		when(historyRepository.findByBattleIdIn(anyCollection())).thenReturn(List.of());
		when(eloService.apply(1L)).thenReturn(false);

		assertThat(recovery.repair()).isZero();
	}

}
