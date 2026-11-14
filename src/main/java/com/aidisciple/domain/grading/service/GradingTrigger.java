package com.aidisciple.domain.grading.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.aidisciple.domain.battle.event.BattleSolvedEvent;

/**
 * 배틀 도메인의 이벤트를 받아 채점을 건다. 풀이 결과를 저장한 트랜잭션이 커밋된 뒤에 받아야 저장된 답을 읽을 수 있다.
 * 배틀 도메인은 채점을 모르고, 이 클래스가 두 도메인을 잇는 유일한 곳이다.
 * 큐가 가득 차 못 걸면 배틀은 RUNNING(두 참가자 SOLVED)으로 남고, 서버가 다시 시작될 때 이벤트가 다시 나가 채점된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradingTrigger {

	private final GradingExecutor executor;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onBattleSolved(BattleSolvedEvent event) {
		try {
			executor.runAsync(event.battleId());
		} catch (TaskRejectedException e) {
			log.warn("작업 큐가 가득 차 채점을 걸지 못함(서버를 다시 시작하면 이어서 채점): battleId={}", event.battleId());
		}
	}

}
