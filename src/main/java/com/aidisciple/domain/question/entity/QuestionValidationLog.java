package com.aidisciple.domain.question.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aidisciple.global.jpa.BaseCreatedEntity;

/**
 * 후보 문항 하나의 단계별 결과. 탈락 문항은 여기에만 남는다 (candidateJson 에 후보 전체).
 * questionId 는 저장된(통과한) 문항에만 있다.
 */
@Getter
@Entity
@Table(name = "question_validation_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionValidationLog extends BaseCreatedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long jobId;

	private Long questionId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ValidationStage stage;

	@Column(nullable = false)
	private boolean passed;

	@Column(precision = 4, scale = 3)
	private BigDecimal score;

	@Column(nullable = false, columnDefinition = "JSON")
	private String candidateJson;

	@Column(columnDefinition = "JSON")
	private String detailJson;

	public static QuestionValidationLog of(Long jobId, Long questionId, ValidationStage stage, boolean passed,
										   String candidateJson, String detailJson) {
		QuestionValidationLog log = new QuestionValidationLog();
		log.jobId = jobId;
		log.questionId = questionId;
		log.stage = stage;
		log.passed = passed;
		log.candidateJson = candidateJson;
		log.detailJson = detailJson;
		return log;
	}

}
