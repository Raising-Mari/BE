package com.aidisciple.domain.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.aidisciple.domain.battle.dto.FinishedRankedBattle;
import com.aidisciple.domain.battle.entity.BattleResult;
import com.aidisciple.domain.battle.service.BattleRecordService;
import com.aidisciple.domain.ranking.entity.EloHistory;
import com.aidisciple.domain.ranking.entity.EloRating;
import com.aidisciple.domain.ranking.repository.EloHistoryRepository;
import com.aidisciple.domain.ranking.repository.EloRatingRepository;

class EloServiceTest {

	private static final long BATTLE_ID = 50L;
	private static final long COURSE_ID = 1L;

	private final BattleRecordService battleRecordService = mock(BattleRecordService.class);
	private final EloRatingRepository ratingRepository = mock(EloRatingRepository.class);
	private final EloHistoryRepository historyRepository = mock(EloHistoryRepository.class);
	private final EloService service = new EloService(battleRecordService, ratingRepository, historyRepository);

	@BeforeEach
	void 기본_상태() {
		when(ratingRepository.findLocked(anyLong(), anyLong())).thenReturn(Optional.empty());
		when(ratingRepository.saveAndFlush(any(EloRating.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private void finished(FinishedRankedBattle.Participant... participants) {
		when(battleRecordService.finishedRanked(BATTLE_ID)).thenReturn(Optional.of(
			new FinishedRankedBattle(BATTLE_ID, COURSE_ID, Instant.parse("2026-11-09T00:00:00Z"), List.of(participants))));
	}

	private static FinishedRankedBattle.Participant participant(long userId, BattleResult result) {
		return new FinishedRankedBattle.Participant(userId, result, BigDecimal.ZERO);
	}

	private void existingRating(long userId, int rating) {
		EloRating existing = EloRating.initial(userId, COURSE_ID);
		existing.applyResult(rating, BattleResult.WIN);   // 레이팅만 원하는 값으로 만들고, 전적은 1승이 된다
		when(ratingRepository.findLocked(userId, COURSE_ID)).thenReturn(Optional.of(existing));
	}

	private List<EloHistory> savedHistories() {
		ArgumentCaptor<EloHistory> saved = ArgumentCaptor.forClass(EloHistory.class);
		verify(historyRepository, times(2)).save(saved.capture());
		return saved.getAllValues();
	}

	@Test
	void 첫_배틀이면_1000점에서_시작해_이긴_쪽이_16점_오르고_진_쪽이_16점_내린다() {
		finished(participant(10L, BattleResult.WIN), participant(20L, BattleResult.LOSE));

		boolean applied = service.apply(BATTLE_ID);

		assertThat(applied).isTrue();
		ArgumentCaptor<EloRating> created = ArgumentCaptor.forClass(EloRating.class);
		verify(ratingRepository, times(2)).saveAndFlush(created.capture());
		EloRating winner = created.getAllValues().get(0);
		EloRating loser = created.getAllValues().get(1);
		assertThat(winner.getUserId()).isEqualTo(10L);
		assertThat(winner.getRating()).isEqualTo(1016);
		assertThat(winner.getGamesPlayed()).isEqualTo(1);
		assertThat(winner.getWins()).isEqualTo(1);
		assertThat(loser.getRating()).isEqualTo(984);
		assertThat(loser.getLosses()).isEqualTo(1);

		List<EloHistory> histories = savedHistories();
		assertThat(histories).extracting(EloHistory::getUserId).containsExactly(10L, 20L);
		assertThat(histories).extracting(EloHistory::getBattleId).containsOnly(BATTLE_ID);
		assertThat(histories).extracting(EloHistory::getCourseId).containsOnly(COURSE_ID);
		assertThat(histories).extracting(EloHistory::getRatingBefore).containsExactly(1000, 1000);
		assertThat(histories).extracting(EloHistory::getRatingAfter).containsExactly(1016, 984);
		assertThat(histories).extracting(EloHistory::getDelta).containsExactly(16, -16);
	}

	@Test
	void 이미_있는_레이팅을_쓰고_높은_쪽이_이기면_조금만_오른다() {
		existingRating(10L, 1200);
		existingRating(20L, 1000);
		finished(participant(10L, BattleResult.WIN), participant(20L, BattleResult.LOSE));

		service.apply(BATTLE_ID);

		List<EloHistory> histories = savedHistories();
		assertThat(histories).extracting(EloHistory::getRatingAfter).containsExactly(1208, 992);
		verify(ratingRepository, never()).saveAndFlush(any());   // 있는 행은 새로 만들지 않는다
	}

	@Test
	void 무승부는_무승부_횟수가_늘고_같은_레이팅이면_변하지_않는다() {
		finished(participant(10L, BattleResult.DRAW), participant(20L, BattleResult.DRAW));

		service.apply(BATTLE_ID);

		ArgumentCaptor<EloRating> created = ArgumentCaptor.forClass(EloRating.class);
		verify(ratingRepository, times(2)).saveAndFlush(created.capture());
		assertThat(created.getAllValues()).extracting(EloRating::getRating).containsExactly(1000, 1000);
		assertThat(created.getAllValues()).extracting(EloRating::getDraws).containsExactly(1, 1);
		assertThat(savedHistories()).extracting(EloHistory::getDelta).containsExactly(0, 0);
	}

	@Test
	void 두_레이팅_행은_참가자_순서와_상관없이_user_id_순으로_잠근다() {
		finished(participant(20L, BattleResult.WIN), participant(10L, BattleResult.LOSE));   // id 큰 쪽이 먼저 온다

		service.apply(BATTLE_ID);

		InOrder order = inOrder(ratingRepository);
		order.verify(ratingRepository).findLocked(10L, COURSE_ID);
		order.verify(ratingRepository).findLocked(20L, COURSE_ID);
		// 이긴 쪽(20)이 올랐다: user_id 순으로 정렬해도 승패는 사람에 붙어 있다
		assertThat(savedHistories()).extracting(EloHistory::getUserId, EloHistory::getDelta)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(10L, -16), org.assertj.core.groups.Tuple.tuple(20L, 16));
	}

	@Test
	void 이미_반영된_배틀은_다시_반영하지_않는다() {
		finished(participant(10L, BattleResult.WIN), participant(20L, BattleResult.LOSE));
		when(historyRepository.existsByBattleId(BATTLE_ID)).thenReturn(true);

		boolean applied = service.apply(BATTLE_ID);

		assertThat(applied).isFalse();
		verify(ratingRepository, never()).findLocked(anyLong(), anyLong());
		verify(historyRepository, never()).save(any());
	}

	@Test
	void 반영할_수_없는_배틀은_아무것도_하지_않는다() {
		when(battleRecordService.finishedRanked(BATTLE_ID)).thenReturn(Optional.empty());   // 연습·미완료·승패 없음

		assertThat(service.apply(BATTLE_ID)).isFalse();
		verify(ratingRepository, never()).findLocked(anyLong(), anyLong());
		verify(historyRepository, never()).existsByBattleId(anyLong());
	}

}
