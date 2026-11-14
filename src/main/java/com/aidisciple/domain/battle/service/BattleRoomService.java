package com.aidisciple.domain.battle.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.battle.config.BattleProperties;
import com.aidisciple.domain.battle.dto.BattleResponse;
import com.aidisciple.domain.battle.dto.CreateBattleRequest;
import com.aidisciple.domain.battle.dto.CreateBattleResponse;
import com.aidisciple.domain.battle.dto.JoinBattleRequest;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.logic.RoomCodes;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.course.dto.DiscipleRef;
import com.aidisciple.domain.course.dto.UnitRef;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.service.CourseService;
import com.aidisciple.domain.course.service.DiscipleService;
import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.domain.teaching.service.NoteLookupService;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.ratelimit.RateLimitProperties;
import com.aidisciple.global.ratelimit.RateLimiter;

/**
 * 방의 생애: 만들기(B-01)·입장(B-02)·취소(B-05)·공개 동의(B-08).
 * 사용자 행을 먼저 잠그고(같은 사람의 요청을 한 줄로) 그다음 배틀 행을 잠근다 — 잠그는 순서를 항상 같게 해 교착을 피한다.
 * AI 는 부르지 않으므로 모두 짧은 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattleRoomService {

	/** 방 만들기(B-01)와 연습 배틀(B-10)이 함께 세는 한도의 이름 */
	static final String RATE_LIMIT_POLICY = "battle.create";
	private static final int CODE_ATTEMPTS = 10;

	private final BattleRepository battleRepository;
	private final BattleParticipantRepository participantRepository;
	private final BattleQueryService queryService;
	private final UserLookupService userLookupService;
	private final CourseService courseService;
	private final UnitService unitService;
	private final DiscipleService discipleService;
	private final NoteLookupService noteLookupService;
	private final QuestionBankService questionBankService;
	private final RateLimiter rateLimiter;
	private final RateLimitProperties rateLimitProperties;
	private final BattleProperties properties;
	private final Clock clock;

	/** B-01 */
	@Transactional
	public CreateBattleResponse create(long userId, CreateBattleRequest request) {
		rateLimiter.check(RATE_LIMIT_POLICY, rateLimitProperties.battleCreate(), String.valueOf(userId));
		UserRef user = userLookupService.lockActive(userId);
		Course course = courseService.requireVisible(request.courseId(), user);
		UnitRef unit = unitService.requireInCourse(course.getId(), request.unitId());
		DiscipleRef disciple = requireDisciple(userId, course.getId());
		Instant now = clock.instant();
		requireNoActiveRanked(userId, now);

		int questionCount = request.questionCountOrDefault();
		if (!questionBankService.readiness(userId, unit.id(), questionCount).ready()) {
			// 부족 사실을 알리는 동시에 재고 보충 작업을 건다. 작업은 자기 트랜잭션(REQUIRES_NEW)이라 이 오류로 되돌려지지 않는다.
			questionBankService.requestRestock(unit.id());
			throw new BusinessException(ErrorCode.BATTLE_QUESTION_BANK_NOT_READY);
		}
		if (noteLookupService.activeNotes(disciple.id(), unit.id()).isEmpty()) {
			throw BattleErrors.notesRequired(unit);
		}

		Battle battle = battleRepository.saveAndFlush(Battle.openRoom(course.getId(), unit.id(), questionCount, userId,
			newRoomCode(), now.plus(properties.roomTtl())));
		participantRepository.save(BattleParticipant.joined(battle.getId(), userId, disciple.id(), true, now));
		return new CreateBattleResponse(battle.getId(), battle.getRoomCode(), battle.getStatus(), battle.getExpiresAt());
	}

	/** B-02. 이미 들어와 있는 입장자가 다시 불러도 같은 응답을 준다(새로고침). */
	@Transactional
	public BattleResponse join(long userId, JoinBattleRequest request) {
		String code = RoomCodes.normalize(request.roomCode());
		if (code == null) {
			throw new BusinessException(ErrorCode.BATTLE_ROOM_NOT_FOUND);
		}
		UserRef user = userLookupService.lockActive(userId);
		Instant now = clock.instant();

		Battle located = locate(code, now);
		Battle battle = battleRepository.findLockedById(located.getId())
			.orElseThrow(() -> new BusinessException(ErrorCode.BATTLE_ROOM_NOT_FOUND));
		// 다른 학교의 방은 없는 방처럼 답한다
		courseService.requireVisible(battle.getCourseId(), user);
		if (battle.isExpiredAt(now) || battle.getStatus() == BattleStatus.EXPIRED) {
			throw new BusinessException(ErrorCode.BATTLE_ROOM_EXPIRED);
		}

		List<BattleParticipant> participants = participantRepository.findByBattleIdOrderByIdAsc(battle.getId());
		BattleParticipant existing = participants.stream().filter(p -> p.getUserId() == userId).findFirst().orElse(null);
		if (existing != null) {
			if (existing.isCreator()) {
				throw new BusinessException(ErrorCode.BATTLE_SELF_JOIN);
			}
			return queryService.toResponse(battle, participants, userId);
		}
		if (battle.getStatus() != BattleStatus.WAITING) {
			// 이미 두 명이 들어와 있거나(LOBBY·시작) 취소·종료된 방
			throw new BusinessException(battle.getStatus() == BattleStatus.LOBBY || battle.getStatus() == BattleStatus.RUNNING
				? ErrorCode.BATTLE_ROOM_FULL : ErrorCode.BATTLE_ROOM_NOT_FOUND);
		}

		DiscipleRef disciple = requireDisciple(userId, battle.getCourseId());
		requireNoActiveRanked(userId, now);
		UnitRef unit = unitService.findRefs(List.of(battle.getUnitId())).get(battle.getUnitId());
		if (noteLookupService.activeNotes(disciple.id(), battle.getUnitId()).isEmpty()) {
			throw BattleErrors.notesRequired(unit);
		}

		BattleParticipant joined = participantRepository.save(
			BattleParticipant.joined(battle.getId(), userId, disciple.id(), false, now));
		battle.markLobby();
		List<BattleParticipant> everyone = new ArrayList<>(participants);
		everyone.add(joined);
		return queryService.toResponse(battle, everyone, userId);
	}

	/** B-05. 참가자 누구나. 이미 취소된 방에 다시 불러도 성공으로 본다(두 번 누름). */
	@Transactional
	public void cancel(long userId, long battleId) {
		Battle battle = battleRepository.findLockedById(battleId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		requireParticipant(battleId, userId);
		if (battle.getStatus() == BattleStatus.CANCELED) {
			return;
		}
		battle.cancel();
	}

	/** B-08 */
	@Transactional
	public void changeNotesPublic(long userId, long battleId, boolean notesPublic) {
		participantRepository.findByBattleIdAndUserId(battleId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND))
			.changeNotesPublic(notesPublic);
	}

	/**
	 * 코드는 대소문자·공백을 이미 맞춘 값. 시작 전 방(active_room_code)을 먼저 찾고, 없으면 지난 방(room_code)에서 가장
	 * 최근 것을 찾아 만료된 방이면 만료로 알려 준다. 그 밖의 상태이거나 방이 없으면 없는 방이다.
	 */
	private Battle locate(String code, Instant now) {
		var open = battleRepository.findByActiveRoomCode(code);
		if (open.isPresent()) {
			return open.get();
		}
		Battle latest = battleRepository.findFirstByRoomCodeOrderByIdDesc(code)
			.orElseThrow(() -> new BusinessException(ErrorCode.BATTLE_ROOM_NOT_FOUND));
		if (latest.getStatus() == BattleStatus.EXPIRED || latest.isExpiredAt(now)) {
			throw new BusinessException(ErrorCode.BATTLE_ROOM_EXPIRED);
		}
		throw new BusinessException(ErrorCode.BATTLE_ROOM_NOT_FOUND);
	}

	private DiscipleRef requireDisciple(long userId, long courseId) {
		return discipleService.findByUserAndCourse(userId, courseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.BATTLE_DISCIPLE_REQUIRED));
	}

	private void requireNoActiveRanked(long userId, Instant now) {
		if (battleRepository.hasInProgress(userId, BattleMode.RANKED, now)) {
			throw new BusinessException(ErrorCode.BATTLE_ALREADY_ACTIVE);
		}
	}

	private void requireParticipant(long battleId, long userId) {
		if (participantRepository.findByBattleIdAndUserId(battleId, userId).isEmpty()) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
	}

	/**
	 * 진행 중인 방과 겹치지 않는 코드. 겹칠 확률은 거의 없지만 확인하고, 그래도 겹치면 active_room_code 의 UNIQUE 가 마지막으로 막는다.
	 */
	private String newRoomCode() {
		for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
			String code = RoomCodes.generate();
			if (battleRepository.findByActiveRoomCode(code).isEmpty()) {
				return code;
			}
		}
		throw new BusinessException(ErrorCode.INTERNAL_ERROR);
	}

}
