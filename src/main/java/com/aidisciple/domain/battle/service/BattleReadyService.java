package com.aidisciple.domain.battle.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.aidisciple.domain.battle.dto.ReadyRequest;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * B-04 준비 완료와 B-09 풀이 재시도의 바깥 문. <b>트랜잭션을 걸지 않는다</b>: DB 변경은 {@link BattleStartStore} 의
 * 트랜잭션이 커밋된 뒤에야 풀이 작업을 접수하고(곧바로 시작되는 풀이 스레드가 커밋 전 상태를 못 보게),
 * 문항 부족 오류도 커밋 뒤에 던진다(예외로 롤백되면 두 사람의 준비를 푸는 변경이 사라진다).
 */
@Service
@RequiredArgsConstructor
public class BattleReadyService {

	private final BattleStartStore store;
	private final BattleSolveDispatcher dispatcher;
	private final QuestionBankService questionBankService;

	/** B-04. 응답 본문은 없다(204). */
	public void ready(long userId, long battleId, ReadyRequest request) {
		BattleStartStore.ReadyOutcome outcome = store.markReady(userId, battleId, request == null ? null : request.notesPublic());
		switch (outcome.result()) {
			case STARTED -> dispatcher.submitAll(outcome.participantIds());
			case NOT_ENOUGH_QUESTIONS -> {
				// 부족을 알리는 김에 재고 보충을 건다. 이미 작업이 있으면 아무 일도 없다.
				questionBankService.requestRestock(outcome.unitId());
				throw new BusinessException(ErrorCode.BATTLE_NOT_ENOUGH_QUESTIONS);
			}
			case WAITING_FOR_OTHER, ALREADY_READY -> {
			}
		}
	}

	/** B-09. 접수만 하고 돌아온다(202). 진행은 B-03 폴링으로 본다. */
	public void retrySolve(long userId, long battleId) {
		List<Long> participantIds = store.retrySolve(userId, battleId);
		dispatcher.submitAll(participantIds);
	}

}
