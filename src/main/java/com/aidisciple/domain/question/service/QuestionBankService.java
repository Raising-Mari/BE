package com.aidisciple.domain.question.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.course.dto.UnitRef;
import com.aidisciple.domain.course.service.CourseService;
import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.domain.question.config.QuestionProperties;
import com.aidisciple.domain.question.dto.BattleReadinessResponse;
import com.aidisciple.domain.question.entity.JobTriggerType;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.entity.UserQuestionHistory;
import com.aidisciple.domain.question.logic.QuestionPicker;
import com.aidisciple.domain.question.repository.QuestionRepository;
import com.aidisciple.domain.question.repository.UserQuestionHistoryRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 문제 은행의 바깥 창구. Q-01 과, 배틀(WP8)이 쓰는 문항 선택·본 문항 기록·재고 보충을 맡는다.
 * 문항 내용은 내보내지 않고 id 와 개수만 내보낸다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionBankService {

	private final QuestionRepository questionRepository;
	private final UserQuestionHistoryRepository historyRepository;
	private final UserLookupService userLookupService;
	private final CourseService courseService;
	private final UnitService unitService;
	private final QuestionJobTrigger jobTrigger;
	private final QuestionProperties properties;

	/** Q-01. 나에게 아직 안 나온, 배틀에 쓸 수 있는 문항 수. 다른 학교의 주차는 없는 주차처럼 404 다. */
	public BattleReadinessResponse readiness(long userId, long unitId, int questionCount) {
		UserRef user = userLookupService.requireActive(userId);
		UnitRef unit = unitService.findRefs(List.of(unitId)).get(unitId);
		if (unit == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		courseService.requireVisible(unit.courseId(), user);
		int available = (int) questionRepository.countUnseen(unitId, usableStatuses(), userId);
		return new BattleReadinessResponse(available >= questionCount, available, questionCount);
	}

	/**
	 * 배틀 시작(B-04) 때 문항 선택: 참가자 누구도 본 적 없는 문항 중 배틀 id 시드로 count 개.
	 * 모자라면 빈 목록 — 호출한 쪽이 BATTLE_NOT_ENOUGH_QUESTIONS 로 처리한다.
	 */
	public List<Long> pickForBattle(long unitId, Collection<Long> userIds, int count, long battleId) {
		List<QuestionPicker.Candidate> candidates = questionRepository
			.findUnseenByAll(unitId, usableStatuses(), userIds).stream()
			.map(question -> new QuestionPicker.Candidate(question.getId(), question.getConceptId()))
			.toList();
		return QuestionPicker.pick(candidates, count, battleId);
	}

	/**
	 * 배틀 풀이(J-03)가 제자에게 보여 줄 문항 본문(id → 본문). 학생에게 내려보내는 값이 아니라 제자 풀이의 입력으로만 쓴다.
	 */
	public Map<Long, String> stems(Collection<Long> questionIds) {
		if (questionIds.isEmpty()) {
			return Map.of();
		}
		return questionRepository.findAllById(questionIds).stream()
			.collect(Collectors.toMap(question -> question.getId(), question -> question.getStem()));
	}

	/**
	 * 연습 배틀(B-10)이 원본 문항을 다시 낼 수 있는가. 신고가 접수됐거나(UNDER_REVIEW) 폐기된(RETIRED) 문항은 뺀다.
	 * 이미 한 번 쓴 문항이라 UNJUDGED 허용 설정과는 무관하다.
	 */
	public Set<Long> practiceUsable(Collection<Long> questionIds) {
		if (questionIds.isEmpty()) {
			return Set.of();
		}
		return questionRepository.findAllById(questionIds).stream()
			.filter(question -> question.getStatus() != QuestionStatus.UNDER_REVIEW && question.getStatus() != QuestionStatus.RETIRED)
			.map(question -> question.getId())
			.collect(Collectors.toSet());
	}

	/** 랭킹 배틀에 고정된 문항을 참가자마다 "본 문항"으로 남긴다. 연습 배틀은 부르지 않는다. */
	@Transactional
	public void recordSeen(Collection<Long> userIds, Collection<Long> questionIds, long battleId) {
		for (Long userId : userIds) {
			for (Long questionId : questionIds) {
				historyRepository.save(UserQuestionHistory.seenInBattle(userId, questionId, battleId));
			}
		}
	}

	/** B-01 에서 문항이 모자랄 때 재고 보충 작업을 건다 (J-02, LOW_STOCK). 이미 작업이 있으면 아무것도 하지 않는다. */
	public void requestRestock(long unitId) {
		jobTrigger.queueAndSubmit(unitId, JobTriggerType.LOW_STOCK);
	}

	/** 배틀에 쓸 수 있는 상태. UNJUDGED 는 설정이 켜졌을 때만 (api-spec 11-2) */
	List<QuestionStatus> usableStatuses() {
		return properties.allowUnjudged()
			? List.of(QuestionStatus.VERIFIED, QuestionStatus.UNJUDGED)
			: List.of(QuestionStatus.VERIFIED);
	}

}
