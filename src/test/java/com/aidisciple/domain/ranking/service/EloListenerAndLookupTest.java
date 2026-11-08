package com.aidisciple.domain.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.event.BattleFinishedEvent;
import com.aidisciple.domain.battle.service.BattleEloLookup;
import com.aidisciple.domain.ranking.entity.EloHistory;
import com.aidisciple.domain.ranking.repository.EloHistoryRepository;

class EloListenerAndLookupTest {

	@Test
	void 랭킹_배틀이_끝나면_Elo를_반영한다() {
		EloService eloService = mock(EloService.class);

		new EloBattleListener(eloService).onBattleFinished(new BattleFinishedEvent(50L, BattleMode.RANKED));

		verify(eloService).apply(50L);
	}

	@Test
	void 연습_배틀이_끝나도_Elo는_바뀌지_않는다() {
		EloService eloService = mock(EloService.class);

		new EloBattleListener(eloService).onBattleFinished(new BattleFinishedEvent(50L, BattleMode.PRACTICE));

		verify(eloService, never()).apply(anyLong());
	}

	@Test
	void 배틀_응답에_싣는_Elo_변화는_이력에서_읽는다() {
		EloHistoryRepository historyRepository = mock(EloHistoryRepository.class);
		when(historyRepository.findByBattleIdAndUserId(50L, 10L)).thenReturn(Optional.of(EloHistory.of(10L, 1L, 50L, 1000, 1016)));
		EloChangeLookup lookup = new EloChangeLookup(historyRepository);

		assertThat(lookup.find(50L, 10L)).contains(new BattleEloLookup.EloChange(1000, 1016, 16));
		assertThat(lookup.find(50L, 99L)).isEmpty();   // 반영 전이거나 이 배틀의 참가자가 아니다
	}

}
