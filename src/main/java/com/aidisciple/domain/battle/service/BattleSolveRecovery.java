package com.aidisciple.domain.battle.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.repository.BattleRepository;

/**
 * J-06 중 배틀 부분. 서버가 시작될 때 끝나지 못한 풀이를 이어서 한다. 풀이에 필요한 값(노트 스냅샷·문항)이 모두 DB 에 있어서
 * 처음부터 다시 돌릴 수 있다. 모두 풀었는데 채점 전이던 배틀은 채점 이벤트를 다시 낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BattleSolveRecovery implements ApplicationRunner {

	private final BattleRepository battleRepository;
	private final BattleSolveStore store;
	private final BattleSolveDispatcher dispatcher;

	@Override
	public void run(ApplicationArguments args) {
		for (Long battleId : battleRepository.findIdsByStatus(BattleStatus.RUNNING)) {
			BattleSolveStore.Recovery recovery = store.recover(battleId);
			if (!recovery.participantIdsToSolve().isEmpty()) {
				log.warn("끝나지 못한 배틀 풀이를 다시 접수합니다: battleId={}, 참가자 {}명", battleId,
					recovery.participantIdsToSolve().size());
				dispatcher.submitAll(recovery.participantIdsToSolve());
			}
		}
	}

}
