package com.aidisciple.domain.battle.service;

import java.util.Collection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

/**
 * 참가자의 풀이 작업을 접수한다. 반드시 접수를 만든 트랜잭션이 <b>커밋된 뒤</b>에 부른다 — 곧바로 시작되는 풀이 스레드가
 * 커밋 전의 배틀·노트 스냅샷을 못 보는 일이 없게. 큐가 가득 차면 조용히 버리지 않고 배틀을 ERROR 로 남긴다(B-09 로 재시도).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BattleSolveDispatcher {

	static final String QUEUE_FULL = "QUEUE_FULL";

	private final BattleSolveExecutor executor;
	private final BattleSolveStore store;

	public void submitAll(Collection<Long> participantIds) {
		participantIds.forEach(this::submit);
	}

	public void submit(long participantId) {
		try {
			executor.runAsync(participantId);
		} catch (TaskRejectedException e) {
			log.warn("작업 큐가 가득 차 배틀 풀이를 실패로 닫음: participantId={}", participantId);
			store.fail(participantId, QUEUE_FULL, 0);
		}
	}

}
