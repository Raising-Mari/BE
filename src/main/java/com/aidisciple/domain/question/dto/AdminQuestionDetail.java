package com.aidisciple.domain.question.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonRawValue;

import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.entity.QuestionSource;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.entity.ValidationStage;
import com.aidisciple.domain.question.logic.KeyElement;

/** X-04 관리자 문항 상세. 정답·모범 답·근거·검증 기록이 모두 들어 있다 — 관리자 전용이다. */
public record AdminQuestionDetail(
	Long id,
	Long courseId,
	Long unitId,
	String stem,
	GradingMode gradingMode,
	List<String> answers,
	List<KeyElement> keyElements,
	Integer requiredElements,
	String referenceAnswer,
	String evidenceText,
	QuestionStatus status,
	QuestionSource source,
	boolean leakTestPassed,
	Long generationJobId,
	Long reviewedBy,
	Instant reviewedAt,
	Instant createdAt,
	long usedCount,
	long answeredCount,
	Double correctRate,
	List<ValidationLog> validationLogs
) {

	/** {@code detailJson} 은 DB 의 JSON 을 그대로(문자열이 아니라 JSON 으로) 내보낸다 */
	public record ValidationLog(ValidationStage stage, boolean passed, BigDecimal score,
								@JsonRawValue String detailJson, Instant createdAt) {
	}

}
