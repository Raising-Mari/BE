package com.aidisciple.domain.battle.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.battle.dto.FinishedRankedBattle;
import com.aidisciple.domain.battle.dto.RankedBattleRecord;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;

/**
 * 랭킹(ranking 도메인)이 끝난 랭킹 배틀의 결과를 읽는 창구. 랭킹은 배틀의 테이블을 직접 만지지 않고 이 서비스만 부른다.
 * 연습 배틀·끝나지 않은 배틀·승패가 아직 없는 배틀은 어느 메서드에서도 나오지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattleRecordService {

	private final BattleRepository battleRepository;
	private final BattleParticipantRepository participantRepository;

	/** Elo 를 반영할 배틀. FINISHED 인 RANKED 이고 참가자 둘의 승패가 모두 정해져 있을 때만 있다. */
	public Optional<FinishedRankedBattle> finishedRanked(long battleId) {
		Battle battle = battleRepository.findById(battleId).orElse(null);
		if (battle == null || battle.getMode() != BattleMode.RANKED || battle.getStatus() != BattleStatus.FINISHED) {
			return Optional.empty();
		}
		List<BattleParticipant> participants = participantRepository.findByBattleIdOrderByIdAsc(battleId);
		if (participants.size() != 2 || participants.stream().anyMatch(participant -> participant.getResult() == null)) {
			return Optional.empty();
		}
		return Optional.of(new FinishedRankedBattle(battleId, battle.getCourseId(), battle.getFinishedAt(),
			participants.stream()
				.map(p -> new FinishedRankedBattle.Participant(p.getUserId(), p.getResult(), p.getTotalScore()))
				.toList()));
	}

	/** 끝난 랭킹 배틀 id 를 끝난 순서대로(서버가 시작될 때 Elo 가 빠진 배틀을 순서대로 반영하려고) */
	public List<Long> finishedRankedBattleIds() {
		return battleRepository.findByModeAndStatusOrderByFinishedAtAscIdAsc(BattleMode.RANKED, BattleStatus.FINISHED).stream()
			.map(Battle::getId)
			.toList();
	}

	/** 내가 한 끝난 랭킹 배틀을 최근 것부터. {@code courseId} 를 주면 그 과목만. */
	public List<RankedBattleRecord> rankedRecordsOf(long userId, Long courseId) {
		List<Battle> battles = battleRepository.findAll(
			BattleQueryService.mineSpec(userId, BattleMode.RANKED, BattleStatus.FINISHED, courseId),
			Sort.by(Sort.Direction.DESC, "finishedAt").and(Sort.by(Sort.Direction.DESC, "id")));
		if (battles.isEmpty()) {
			return List.of();
		}
		Map<Long, List<BattleParticipant>> participantsByBattle = participantRepository
			.findByBattleIdIn(battles.stream().map(Battle::getId).toList()).stream()
			.collect(Collectors.groupingBy(BattleParticipant::getBattleId));
		return battles.stream()
			.map(battle -> toRecord(battle, participantsByBattle.getOrDefault(battle.getId(), List.of()), userId))
			.flatMap(Optional::stream)
			.toList();
	}

	/** 배틀 id → 그 배틀에서 {@code userId} 의 상대 id (Elo 변동 이력에 상대를 붙일 때) */
	public Map<Long, Long> opponentIds(long userId, Collection<Long> battleIds) {
		if (battleIds.isEmpty()) {
			return Map.of();
		}
		return participantRepository.findByBattleIdIn(battleIds).stream()
			.filter(participant -> participant.getUserId() != userId)
			.collect(Collectors.toMap(BattleParticipant::getBattleId, BattleParticipant::getUserId, (first, second) -> first));
	}

	private static Optional<RankedBattleRecord> toRecord(Battle battle, List<BattleParticipant> participants, long userId) {
		BattleParticipant me = participants.stream().filter(p -> p.getUserId() == userId).findFirst().orElse(null);
		BattleParticipant opponent = participants.stream().filter(p -> p.getUserId() != userId).findFirst().orElse(null);
		if (me == null || opponent == null || me.getResult() == null) {
			return Optional.empty();
		}
		return Optional.of(new RankedBattleRecord(battle.getId(), battle.getCourseId(), battle.getFinishedAt(), me.getResult(),
			me.getTotalScore(), opponent.getUserId(), opponent.getTotalScore()));
	}

}
