package com.aidisciple.domain.question.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.battle.dto.QuestionUsage;
import com.aidisciple.domain.battle.service.BattleQuestionStatsService;
import com.aidisciple.domain.question.dto.AdminQuestionDetail;
import com.aidisciple.domain.question.dto.AdminQuestionSummary;
import com.aidisciple.domain.question.dto.GradingQuestion;
import com.aidisciple.domain.question.entity.Question;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.repository.QuestionRepository;
import com.aidisciple.domain.question.repository.QuestionValidationLogRepository;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.api.Pages;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 관리자 문항 관리 X-03·X-04·X-05. 권한은 SecurityConfig 가 /api/admin/** 경로로 이미 막는다.
 * 사용 횟수·정답률은 배틀 도메인의 {@link BattleQuestionStatsService} 로만 읽는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQuestionService {

	private final QuestionRepository questionRepository;
	private final QuestionValidationLogRepository validationLogRepository;
	private final QuestionLookupService questionLookupService;
	private final BattleQuestionStatsService statsService;
	private final Clock clock;

	/** X-03: 최신순. status=UNJUDGED 로 검수 대기 문항만 본다. */
	public PageResponse<AdminQuestionSummary> list(Long unitId, QuestionStatus status, Integer page, Integer size) {
		Page<Question> found = questionRepository.search(unitId, status,
			Pages.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
		Map<Long, QuestionUsage> usage = statsService.usageOf(found.getContent().stream().map(Question::getId).toList());
		return PageResponse.from(found, question -> toSummary(question,
			usage.getOrDefault(question.getId(), QuestionUsage.none(question.getId()))));
	}

	/** X-04 */
	public AdminQuestionDetail detail(long questionId) {
		Question question = questionRepository.findById(questionId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		GradingQuestion grading = questionLookupService.gradingQuestions(List.of(questionId)).get(questionId);
		QuestionUsage usage = statsService.usageOf(List.of(questionId))
			.getOrDefault(questionId, QuestionUsage.none(questionId));
		List<AdminQuestionDetail.ValidationLog> logs = validationLogRepository.findByQuestionIdOrderByIdAsc(questionId)
			.stream()
			.map(log -> new AdminQuestionDetail.ValidationLog(log.getStage(), log.isPassed(), log.getScore(),
				log.getDetailJson(), log.getCreatedAt()))
			.toList();
		return new AdminQuestionDetail(question.getId(), question.getCourseId(), question.getUnitId(), question.getStem(),
			question.getGradingMode(), grading.answers(), grading.keyElements(), question.getRequiredElements(),
			question.getReferenceAnswer(), question.getEvidenceText(), question.getStatus(), question.getSource(),
			question.isLeakTestPassed(), question.getGenerationJobId(), question.getReviewedBy(), question.getReviewedAt(),
			question.getCreatedAt(), usage.usedCount(), usage.answeredCount(), usage.correctRate(), logs);
	}

	/** X-05: 검수. 바뀐 상세를 돌려준다. */
	@Transactional
	public AdminQuestionDetail changeStatus(long adminId, long questionId, QuestionStatus next) {
		Question question = questionRepository.findById(questionId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		question.reviewByAdmin(next, adminId, clock.instant());
		return detail(questionId);
	}

	private static AdminQuestionSummary toSummary(Question question, QuestionUsage usage) {
		return new AdminQuestionSummary(question.getId(), question.getCourseId(), question.getUnitId(), question.getStem(),
			question.getGradingMode(), question.getStatus(), question.getSource(), question.isLeakTestPassed(),
			usage.usedCount(), usage.answeredCount(), usage.correctRate(), question.getCreatedAt());
	}

}
