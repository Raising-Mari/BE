package com.aidisciple.domain.ranking.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.aidisciple.domain.battle.service.BattleRecordService;
import com.aidisciple.domain.ranking.entity.EloHistory;
import com.aidisciple.domain.ranking.repository.EloHistoryRepository;

/**
 * 서버가 시작될 때 Elo 가 빠진 끝난 랭킹 배틀을 <b>끝난 순서대로</b> 반영한다(J-06 의 랭킹 부분). 평소에는 없어야 하는 경우다:
 * Elo 가 생기기 전에 끝난 배틀(채점이 먼저 배포된 경우), 또는 반영 코드의 버그가 고쳐지기 전에 끝난 배틀.
 * 배틀마다 트랜잭션이 따로고 {@link EloService#apply} 가 멱등이라 여러 번 돌려도 안전하다. 한 배틀의 실패가 다른 배틀을 막지 않는다.
 */
@Slf4j
@Component
public class EloRecovery implements ApplicationRunner {

	private final BattleRecordService battleRecordService;
	private final EloHistoryRepository historyRepository;
	private final EloService eloService;
	private final TransactionTemplate transactionTemplate;

	public EloRecovery(BattleRecordService battleRecordService, EloHistoryRepository historyRepository, EloService eloService,
					   PlatformTransactionManager transactionManager) {
		this.battleRecordService = battleRecordService;
		this.historyRepository = historyRepository;
		this.eloService = eloService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Override
	public void run(ApplicationArguments args) {
		int applied = repair();
		if (applied > 0) {
			log.warn("Elo 가 빠져 있던 끝난 랭킹 배틀 {}건을 반영했습니다.", applied);
		}
	}

	/** @return 이번에 반영한 배틀 수 */
	int repair() {
		List<Long> finished = battleRecordService.finishedRankedBattleIds();
		if (finished.isEmpty()) {
			return 0;
		}
		Set<Long> done = historyRepository.findByBattleIdIn(finished).stream().map(EloHistory::getBattleId)
			.collect(Collectors.toSet());
		int applied = 0;
		for (Long battleId : finished) {
			if (done.contains(battleId)) {
				continue;
			}
			try {
				if (Boolean.TRUE.equals(transactionTemplate.execute(status -> eloService.apply(battleId)))) {
					applied++;
				}
			} catch (RuntimeException e) {
				log.warn("Elo 반영 실패: battleId={}, cause={}", battleId, e.getClass().getSimpleName());
			}
		}
		return applied;
	}

}
