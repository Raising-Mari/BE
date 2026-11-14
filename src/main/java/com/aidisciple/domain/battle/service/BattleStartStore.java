package com.aidisciple.domain.battle.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.logic.NoteIdsJson;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.course.dto.UnitRef;
import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.domain.teaching.dto.NoteRef;
import com.aidisciple.domain.teaching.service.NoteLookupService;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 준비 완료(B-04)와 풀이 재시도(B-09)의 DB 단계. 배틀 행을 잠근 트랜잭션 하나에서 판단하고 바꾼다.
 * 풀이 작업 접수는 이 트랜잭션이 커밋된 뒤에 {@link BattleReadyService} 가 한다 — 이 클래스는 무엇을 접수할지만 돌려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattleStartStore {

	private final BattleRepository battleRepository;
	private final BattleParticipantRepository participantRepository;
	private final BattleQuestionRepository questionRepository;
	private final NoteLookupService noteLookupService;
	private final QuestionBankService questionBankService;
	private final UnitService unitService;
	private final Clock clock;

	public enum ReadyResult {
		/** 두 사람이 모두 준비해 문항을 고정하고 RUNNING 이 됐다. 풀이를 접수해야 한다. */
		STARTED,
		/** 내 준비는 반영됐고 상대를 기다린다. */
		WAITING_FOR_OTHER,
		/** 이미 준비한 사람이 다시 눌렀다(멱등). */
		ALREADY_READY,
		/** 둘 다 안 본 문항이 모자라 시작하지 못했다. 두 사람의 준비와 스냅샷은 풀렸다. */
		NOT_ENOUGH_QUESTIONS
	}

	/** {@code participantIds} 는 STARTED 일 때만, {@code unitId} 는 재고 보충을 걸 NOT_ENOUGH_QUESTIONS 일 때 쓴다. */
	public record ReadyOutcome(ReadyResult result, List<Long> participantIds, long unitId) {

		static ReadyOutcome of(ReadyResult result, long unitId) {
			return new ReadyOutcome(result, List.of(), unitId);
		}

	}

	/**
	 * B-04. 누른 순간의 ACTIVE 노트를 스냅샷하고(이미 준비했으면 덮어쓰지 않는다), 두 사람이 모두 준비되면
	 * 문항 선택·고정, 본 문항 기록, RUNNING 전환을 한 번에 한다. 배틀 행 잠금 덕에 동시에 눌러도 시작은 한 번이다.
	 */
	@Transactional
	public ReadyOutcome markReady(long userId, long battleId, Boolean notesPublic) {
		Battle battle = battleRepository.findLockedById(battleId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		BattleParticipant me = participantRepository.findByBattleIdAndUserId(battleId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		Instant now = clock.instant();

		if (battle.getStatus() == BattleStatus.EXPIRED || battle.isExpiredAt(now)) {
			throw new BusinessException(ErrorCode.BATTLE_ROOM_EXPIRED);
		}
		if (me.isReady() && isStartedOrLobby(battle.getStatus())) {
			return ReadyOutcome.of(ReadyResult.ALREADY_READY, battle.getUnitId());
		}
		if (battle.getStatus() != BattleStatus.LOBBY) {
			// 상대가 오기 전(WAITING)이거나 취소된 방
			throw new BusinessException(ErrorCode.CONFLICT);
		}

		List<NoteRef> notes = noteLookupService.activeNotes(me.getDiscipleId(), battle.getUnitId());
		if (notes.isEmpty()) {
			UnitRef unit = unitService.findRefs(List.of(battle.getUnitId())).get(battle.getUnitId());
			throw BattleErrors.notesRequired(unit);
		}
		me.markReady(NoteIdsJson.write(notes.stream().map(NoteRef::id).toList()), notes.size(), notesPublic, now);

		List<BattleParticipant> participants = participantRepository.findByBattleIdOrderByIdAsc(battleId);
		if (participants.size() < 2 || !participants.stream().allMatch(BattleParticipant::isReady)) {
			return ReadyOutcome.of(ReadyResult.WAITING_FOR_OTHER, battle.getUnitId());
		}
		return start(battle, participants, now);
	}

	/**
	 * B-09. ERROR 배틀을 RUNNING 으로 되돌리고 실패한 참가자만 다시 접수 상태로 만든다(이미 푼 참가자의 답은 그대로).
	 * 접수해야 할 참가자 id 를 돌려준다. ERROR 가 아니면 CONFLICT.
	 */
	@Transactional
	public List<Long> retrySolve(long userId, long battleId) {
		Battle battle = battleRepository.findLockedById(battleId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		participantRepository.findByBattleIdAndUserId(battleId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		battle.retrySolve();

		List<Long> toSolve = new ArrayList<>();
		for (BattleParticipant participant : participantRepository.findByBattleIdOrderByIdAsc(battleId)) {
			if (participant.resetSolveForRetry()) {
				toSolve.add(participant.getId());
			}
		}
		return toSolve;
	}

	private ReadyOutcome start(Battle battle, List<BattleParticipant> participants, Instant now) {
		List<Long> userIds = participants.stream().map(BattleParticipant::getUserId).toList();
		List<Long> picked = questionBankService.pickForBattle(battle.getUnitId(), userIds, battle.getQuestionCount(),
			battle.getId());
		if (picked.size() < battle.getQuestionCount()) {
			// 시작하지 않고 LOBBY 에 머문다. 다시 누를 때 새로 찍도록 두 사람의 준비와 스냅샷을 비운다.
			participants.forEach(BattleParticipant::resetReady);
			return ReadyOutcome.of(ReadyResult.NOT_ENOUGH_QUESTIONS, battle.getUnitId());
		}
		for (int i = 0; i < picked.size(); i++) {
			questionRepository.save(BattleQuestion.of(battle.getId(), picked.get(i), i + 1, null));
		}
		questionBankService.recordSeen(userIds, picked, battle.getId());
		battle.start(now);
		return new ReadyOutcome(ReadyResult.STARTED, participants.stream().map(BattleParticipant::getId).toList(),
			battle.getUnitId());
	}

	private static boolean isStartedOrLobby(BattleStatus status) {
		return status == BattleStatus.LOBBY || status == BattleStatus.RUNNING || status == BattleStatus.FINISHED
			|| status == BattleStatus.ERROR;
	}

}
