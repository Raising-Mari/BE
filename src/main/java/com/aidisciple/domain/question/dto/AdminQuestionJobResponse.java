package com.aidisciple.domain.question.dto;

import java.time.Instant;
import java.util.Map;

import com.aidisciple.domain.question.entity.JobStatus;
import com.aidisciple.domain.question.entity.JobTriggerType;

/** X-02 문항 생성 작업 진행. {@code rejectedByStage} 는 단계 이름 → 탈락 수 */
public record AdminQuestionJobResponse(
	Long id,
	Long unitId,
	JobTriggerType triggerType,
	Long requestedBy,
	int targetCount,
	JobStatus status,
	int generatedCount,
	int passedCount,
	int rejectedCount,
	Map<String, Long> rejectedByStage,
	String failReason,
	Instant startedAt,
	Instant finishedAt,
	Instant createdAt
) {
}
