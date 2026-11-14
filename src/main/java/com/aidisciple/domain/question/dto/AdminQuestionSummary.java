package com.aidisciple.domain.question.dto;

import java.time.Instant;

import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.entity.QuestionSource;
import com.aidisciple.domain.question.entity.QuestionStatus;

/** X-03 관리자 문항 목록 한 줄. {@code correctRate} 는 채점된 답이 없으면 null */
public record AdminQuestionSummary(
	Long id,
	Long courseId,
	Long unitId,
	String stem,
	GradingMode gradingMode,
	QuestionStatus status,
	QuestionSource source,
	boolean leakTestPassed,
	long usedCount,
	long answeredCount,
	Double correctRate,
	Instant createdAt
) {
}
