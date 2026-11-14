package com.aidisciple.domain.ranking.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.battle.dto.FinishedRankedBattle;
import com.aidisciple.domain.battle.service.BattleRecordService;
import com.aidisciple.domain.ranking.entity.EloHistory;
import com.aidisciple.domain.ranking.entity.EloRating;
import com.aidisciple.domain.ranking.logic.EloCalculator;
import com.aidisciple.domain.ranking.repository.EloHistoryRepository;
import com.aidisciple.domain.ranking.repository.EloRatingRepository;

/**
 * 끝난 랭킹 배틀의 Elo 를 반영한다(api-spec 11-5). 두 사람의 레이팅·전적과 변동 이력을 <b>한 트랜잭션</b>에서 바꾼다.
 * <ul>
 *   <li>멱등: 이미 반영된 배틀이면 아무것도 하지 않는다(미리 확인 + {@code UNIQUE(battle_id, user_id)} 가 마지막 보루).</li>
 *   <li>교착 방지: 두 레이팅 행을 항상 <b>user_id 순으로</b> {@code FOR UPDATE} 로 잠근다(없으면 1000점으로 만든다).</li>
 *   <li>연습 배틀·끝나지 않은 배틀·승패가 없는 배틀은 반영하지 않는다({@link BattleRecordService#finishedRanked} 가 걸러 낸다).</li>
 * </ul>
 * 채점 정산({@code BattleSettlementService.settle})의 트랜잭션 안에서 불리므로 배틀이 FINISHED 가 되면 Elo 도 항상 반영돼 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EloService {

	private final BattleRecordService battleRecordService;
	private final EloRatingRepository ratingRepository;
	private final EloHistoryRepository historyRepository;

	/** @return 이번 호출로 반영했으면 true. 반영할 수 없는 배틀이거나 이미 반영됐으면 false */
	@Transactional
	public boolean apply(long battleId) {
		Optional<FinishedRankedBattle> found = battleRecordService.finishedRanked(battleId);
		if (found.isEmpty() || historyRepository.existsByBattleId(battleId)) {
			return false;
		}
		FinishedRankedBattle battle = found.get();
		List<FinishedRankedBattle.Participant> ordered = battle.participants().stream()
			.sorted(Comparator.comparingLong(FinishedRankedBattle.Participant::userId))
			.toList();
		FinishedRankedBattle.Participant first = ordered.get(0);
		FinishedRankedBattle.Participant second = ordered.get(1);

		EloRating firstRating = lockOrCreate(first.userId(), battle.courseId());
		EloRating secondRating = lockOrCreate(second.userId(), battle.courseId());
		int firstBefore = firstRating.getRating();
		int secondBefore = secondRating.getRating();
		EloCalculator.Change firstChange = EloCalculator.change(firstBefore, secondBefore, first.result());
		EloCalculator.Change secondChange = EloCalculator.change(secondBefore, firstBefore, second.result());

		firstRating.applyResult(firstChange.after(), first.result());
		secondRating.applyResult(secondChange.after(), second.result());
		historyRepository.save(EloHistory.of(first.userId(), battle.courseId(), battleId, firstChange.before(), firstChange.after()));
		historyRepository.save(EloHistory.of(second.userId(), battle.courseId(), battleId, secondChange.before(), secondChange.after()));
		return true;
	}

	/** 첫 배틀이면 1000점 행을 만든다. 만든 행은 이 트랜잭션이 이미 쓰고 있어 잠긴 것과 같다. */
	private EloRating lockOrCreate(long userId, long courseId) {
		return ratingRepository.findLocked(userId, courseId)
			.orElseGet(() -> ratingRepository.saveAndFlush(EloRating.initial(userId, courseId)));
	}

}
