package com.aidisciple.domain.battle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.battle.dto.FinishedRankedBattle;
import com.aidisciple.domain.battle.dto.RankedBattleRecord;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleResult;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;

class BattleRecordServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
	private static final long BATTLE_ID = 50L;
	private static final long ME = 10L;
	private static final long FRIEND = 11L;

	private final BattleRepository battleRepository = mock(BattleRepository.class);
	private final BattleParticipantRepository participantRepository = mock(BattleParticipantRepository.class);
	private final BattleRecordService service = new BattleRecordService(battleRepository, participantRepository);

	private Battle battle;
	private BattleParticipant mine;
	private BattleParticipant friends;

	@BeforeEach
	void 기본_상태() {
		battle = ranked(BATTLE_ID, BattleStatus.FINISHED);
		mine = participant(BATTLE_ID, 5L, ME, "2.00", BattleResult.WIN);
		friends = participant(BATTLE_ID, 6L, FRIEND, "1.00", BattleResult.LOSE);
		when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(mine, friends));
	}

	private static Battle ranked(long id, BattleStatus status) {
		Battle battle = Battle.openRoom(1L, 2L, 3, ME, "K7M4QX", NOW);
		ReflectionTestUtils.setField(battle, "id", id);
		ReflectionTestUtils.setField(battle, "status", status);
		ReflectionTestUtils.setField(battle, "finishedAt", NOW);
		return battle;
	}

	private static BattleParticipant participant(long battleId, long id, long userId, String score, BattleResult result) {
		BattleParticipant participant = BattleParticipant.joined(battleId, userId, userId * 10, userId == ME, NOW);
		ReflectionTestUtils.setField(participant, "id", id);
		participant.applyResult(new BigDecimal(score), 0, 0, 0, 0, 0, result);
		return participant;
	}

	@Test
	void 끝난_랭킹_배틀은_참가자_둘의_승패와_점수를_준다() {
		FinishedRankedBattle result = service.finishedRanked(BATTLE_ID).orElseThrow();

		assertThat(result.battleId()).isEqualTo(BATTLE_ID);
		assertThat(result.courseId()).isEqualTo(1L);
		assertThat(result.finishedAt()).isEqualTo(NOW);
		assertThat(result.participants()).extracting(FinishedRankedBattle.Participant::userId).containsExactly(ME, FRIEND);
		assertThat(result.participants()).extracting(FinishedRankedBattle.Participant::result)
			.containsExactly(BattleResult.WIN, BattleResult.LOSE);
	}

	@Test
	void 연습_배틀과_끝나지_않은_배틀과_없는_배틀은_반영할_수_없다() {
		ReflectionTestUtils.setField(battle, "mode", BattleMode.PRACTICE);
		assertThat(service.finishedRanked(BATTLE_ID)).isEmpty();

		ReflectionTestUtils.setField(battle, "mode", BattleMode.RANKED);
		for (BattleStatus status : List.of(BattleStatus.RUNNING, BattleStatus.ERROR, BattleStatus.CANCELED)) {
			ReflectionTestUtils.setField(battle, "status", status);
			assertThat(service.finishedRanked(BATTLE_ID)).isEmpty();
		}
		when(battleRepository.findById(404L)).thenReturn(Optional.empty());
		assertThat(service.finishedRanked(404L)).isEmpty();
	}

	@Test
	void 승패가_정해지지_않았거나_참가자가_둘이_아니면_반영할_수_없다() {
		BattleParticipant undecided = BattleParticipant.joined(BATTLE_ID, 6L, FRIEND, false, NOW);
		ReflectionTestUtils.setField(undecided, "id", 6L);
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(mine, undecided));
		assertThat(service.finishedRanked(BATTLE_ID)).isEmpty();

		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(mine));
		assertThat(service.finishedRanked(BATTLE_ID)).isEmpty();
	}

	@Test
	void 끝난_랭킹_배틀_id는_리포지토리가_정한_끝난_순서_그대로다() {
		Battle first = ranked(1L, BattleStatus.FINISHED);
		Battle second = ranked(2L, BattleStatus.FINISHED);
		when(battleRepository.findByModeAndStatusOrderByFinishedAtAscIdAsc(BattleMode.RANKED, BattleStatus.FINISHED))
			.thenReturn(List.of(first, second));

		assertThat(service.finishedRankedBattleIds()).containsExactly(1L, 2L);
	}

	@Test
	@SuppressWarnings("unchecked")
	void 내_전적은_내_승패와_상대와_양쪽_점수를_담아_준다() {
		Battle second = ranked(51L, BattleStatus.FINISHED);
		BattleParticipant myDraw = participant(51L, 7L, ME, "1.00", BattleResult.DRAW);
		BattleParticipant otherDraw = participant(51L, 8L, 12L, "1.00", BattleResult.DRAW);
		when(battleRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(second, battle));
		when(participantRepository.findByBattleIdIn(anyCollection())).thenReturn(List.of(mine, friends, myDraw, otherDraw));

		List<RankedBattleRecord> records = service.rankedRecordsOf(ME, null);

		assertThat(records).extracting(RankedBattleRecord::battleId).containsExactly(51L, BATTLE_ID);
		assertThat(records.get(0).result()).isEqualTo(BattleResult.DRAW);
		assertThat(records.get(0).opponentId()).isEqualTo(12L);
		RankedBattleRecord win = records.get(1);
		assertThat(win.result()).isEqualTo(BattleResult.WIN);
		assertThat(win.opponentId()).isEqualTo(FRIEND);
		assertThat(win.myScore()).isEqualByComparingTo("2.00");
		assertThat(win.opponentScore()).isEqualByComparingTo("1.00");
		assertThat(win.courseId()).isEqualTo(1L);
	}

	@Test
	@SuppressWarnings("unchecked")
	void 전적이_없으면_빈_목록이고_참가자를_조회하지_않는다() {
		when(battleRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

		assertThat(service.rankedRecordsOf(ME, 1L)).isEmpty();
		verifyNoInteractions(participantRepository);
	}

	@Test
	void 배틀별_상대_id를_준다() {
		BattleParticipant otherMine = participant(51L, 7L, ME, "1.00", BattleResult.DRAW);
		BattleParticipant otherOpponent = participant(51L, 8L, 12L, "1.00", BattleResult.DRAW);
		when(participantRepository.findByBattleIdIn(anyCollection())).thenReturn(List.of(mine, friends, otherMine, otherOpponent));

		Map<Long, Long> opponents = service.opponentIds(ME, List.of(BATTLE_ID, 51L));

		assertThat(opponents).containsEntry(BATTLE_ID, FRIEND).containsEntry(51L, 12L);
		assertThat(service.opponentIds(ME, List.of())).isEmpty();
	}

}
