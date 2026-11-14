package com.aidisciple.domain.question.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.domain.question.config.QuestionProperties;
import com.aidisciple.domain.question.dto.AdminQuestionJobResponse;
import com.aidisciple.domain.question.dto.CreateQuestionJobResponse;
import com.aidisciple.domain.question.entity.JobTriggerType;
import com.aidisciple.domain.question.entity.QuestionGenerationJob;
import com.aidisciple.domain.question.repository.QuestionGenerationJobRepository;
import com.aidisciple.domain.question.repository.QuestionValidationLogRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 관리자 문항 생성 X-01·X-02. 트랜잭션이 없는 바깥 문이다 — 작업 접수(queue)는 자기 트랜잭션(REQUIRES_NEW)으로
 * 먼저 커밋되고, 그 뒤에 실행기로 넘긴다(곧바로 도는 작업 스레드가 커밋 전 상태를 보지 않게).
 */
@Service
@RequiredArgsConstructor
public class AdminQuestionJobService {

	private final QuestionJobService jobService;
	private final QuestionJobTrigger jobTrigger;
	private final QuestionGenerationJobRepository jobRepository;
	private final QuestionValidationLogRepository validationLogRepository;
	private final UnitService unitService;
	private final QuestionProperties properties;

	/** X-01: 같은 주차에 대기·실행 중인 작업이 있으면 409 */
	public CreateQuestionJobResponse create(long adminId, long unitId, Integer targetCount) {
		if (!unitService.findRefs(List.of(unitId)).containsKey(unitId)) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		int target = targetCount == null ? properties.targetCount() : targetCount;
		long jobId = jobService.queue(unitId, JobTriggerType.ADMIN, adminId, target)
			.orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "이 주차에 이미 대기·실행 중인 문항 생성 작업이 있습니다."));
		jobTrigger.submit(jobId);
		return new CreateQuestionJobResponse(jobId);
	}

	/** X-02 */
	public AdminQuestionJobResponse get(long jobId) {
		QuestionGenerationJob job = jobRepository.findById(jobId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		Map<String, Long> rejectedByStage = new LinkedHashMap<>();
		for (Object[] row : validationLogRepository.countRejectedByStage(jobId)) {
			rejectedByStage.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
		}
		return new AdminQuestionJobResponse(job.getId(), job.getUnitId(), job.getTriggerType(), job.getRequestedBy(),
			job.getTargetCount(), job.getStatus(), job.getGeneratedCount(), job.getPassedCount(), job.getRejectedCount(),
			rejectedByStage, job.getFailReason(), job.getStartedAt(), job.getFinishedAt(), job.getCreatedAt());
	}

}
