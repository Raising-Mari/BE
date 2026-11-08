package com.aidisciple.domain.battle.service;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.aidisciple.ai.AiException;
import com.aidisciple.ai.AiPurpose;
import com.aidisciple.domain.battle.config.BattleProperties;
import com.aidisciple.domain.question.service.DiscipleSolver;
import com.aidisciple.domain.teaching.dto.NoteRef;

/**
 * 참가자 한 명의 제자 풀이를 끝까지 돌린다(J-03). <b>트랜잭션을 걸지 않는다</b>: AI 를 부르는 동안 DB 커넥션을 쥐지 않기 위해서다.
 * 입력은 그 참가자 노트(스냅샷)와 문항뿐이고, 한 참가자에 AI 는 한 번 부른다(전 문항 일괄). 실패하면 백오프를 두고 다시 시도하며
 * 끝내 실패하면 배틀이 ERROR 가 된다. 실패 이유에는 오류 코드나 예외 클래스 이름만 남긴다(노트·답이 섞이지 않게).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BattleSolveRunner {

	static final String RELATED_TYPE = "BATTLE";

	private final BattleSolveStore store;
	private final DiscipleSolver solver;
	private final BattleProperties properties;

	public void run(long participantId) {
		Optional<BattleSolveStore.SolveContext> started = store.begin(participantId);
		if (started.isEmpty()) {
			return;
		}
		BattleSolveStore.SolveContext context = started.get();
		int attempts = 0;
		try {
			if (context.notes().isEmpty()) {
				// 노트가 없으면 어떤 문항도 노트를 인용해 답할 수 없다 — AI 를 부르지 않고 모두 모름으로 둔다(연습 배틀에서만 생긴다).
				store.complete(participantId, allUnknown(context), 0);
				return;
			}
			List<DiscipleSolver.SolvedAnswer> answers = null;
			AiException lastFailure = null;
			while (answers == null && attempts < properties.solveMaxAttempts()) {
				attempts++;
				try {
					answers = solver.solve(inputOf(context));
				} catch (AiException e) {
					lastFailure = e;
					log.warn("제자 풀이 AI 호출 실패: battleId={}, participantId={}, attempt={}, code={}",
						context.battleId(), participantId, attempts, e.getErrorCode());
					if (attempts < properties.solveMaxAttempts()) {
						backoff(attempts);
					}
				}
			}
			if (answers == null) {
				throw lastFailure == null ? new IllegalStateException("NO_ATTEMPT") : lastFailure;
			}
			store.complete(participantId, answers, attempts);
		} catch (AiException e) {
			store.fail(participantId, e.getErrorCode().name(), attempts);
		} catch (RuntimeException e) {
			log.warn("제자 풀이 실패: battleId={}, participantId={}, cause={}", context.battleId(), participantId,
				e.getClass().getSimpleName());
			store.fail(participantId, e.getClass().getSimpleName(), attempts);
		}
	}

	private DiscipleSolver.SolveInput inputOf(BattleSolveStore.SolveContext context) {
		List<DiscipleSolver.SolveNote> notes = context.notes().stream()
			.map((NoteRef note) -> new DiscipleSolver.SolveNote(note.id(), note.content()))
			.toList();
		return new DiscipleSolver.SolveInput(AiPurpose.BATTLE_SOLVE, context.userId(), RELATED_TYPE, context.battleId(), notes,
			context.questions(), true);
	}

	private static List<DiscipleSolver.SolvedAnswer> allUnknown(BattleSolveStore.SolveContext context) {
		return context.questions().stream()
			.map(question -> new DiscipleSolver.SolvedAnswer(question.id(), "", true, List.of(), false))
			.toList();
	}

	/** 시도마다 대기를 2배로 늘린다. 인터럽트(서버 종료)면 더 기다리지 않고 실패로 닫는다. */
	private void backoff(int attemptsSoFar) {
		long millis = properties.solveRetryBackoff().toMillis() << (attemptsSoFar - 1);
		if (millis <= 0) {
			return;
		}
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("INTERRUPTED", e);
		}
	}

}
