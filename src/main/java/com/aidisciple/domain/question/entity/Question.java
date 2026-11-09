package com.aidisciple.domain.question.entity;

import java.math.BigDecimal;
import java.time.Instant;

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

import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.jpa.BaseTimeEntity;

/**
 * 검증을 통과한 문항. 탈락 문항은 이 테이블에 넣지 않고 question_validation_logs 에만 남긴다.
 * 문항·정답·핵심 요소는 학생용 API 응답에 절대 담지 않는다 (복기의 정답 보기 R-11 뒤만 예외).
 */
@Getter
@Entity
@Table(name = "questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long courseId;

	@Column(nullable = false)
	private Long unitId;

	/** 개념 추출을 생략해서(backend-plan D-7) 지금은 항상 비어 있다 */
	private Long conceptId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String stem;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 15)
	private GradingMode gradingMode;

	@Column(columnDefinition = "JSON")
	private String answersJson;

	@Column(columnDefinition = "JSON")
	private String keyElementsJson;

	@Column(columnDefinition = "TINYINT")
	private Integer requiredElements;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String referenceAnswer;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String evidenceText;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private QuestionStatus status;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private QuestionSource source;

	@Column(nullable = false)
	private boolean leakTestPassed;

	@Column(precision = 4, scale = 3)
	private BigDecimal externalJudgeScore;

	@Column(length = 50)
	private String externalJudgeModel;

	private Long generationJobId;

	private Long reviewedBy;

	private Instant reviewedAt;

	/** AI 가 만들고 1~3단계(외부 판정 전이면 UNJUDGED)를 통과한 문항 */
	public static Question fromAi(Long courseId, Long unitId, String stem, GradingMode gradingMode,
								  String answersJson, String keyElementsJson, Integer requiredElements, String referenceAnswer,
								  String evidenceText, QuestionStatus status, Long generationJobId) {
		Question question = new Question();
		question.courseId = courseId;
		question.unitId = unitId;
		question.stem = stem;
		question.gradingMode = gradingMode;
		question.answersJson = answersJson;
		question.keyElementsJson = keyElementsJson;
		question.requiredElements = requiredElements;
		question.referenceAnswer = referenceAnswer;
		question.evidenceText = evidenceText;
		question.status = status;
		question.source = QuestionSource.AI;
		question.leakTestPassed = true;
		question.generationJobId = generationJobId;
		return question;
	}

	/** 근거 자료가 지워지거나 주차 연결이 풀렸다. 이후 배틀에서 빠진다. */
	public void retire() {
		this.status = QuestionStatus.RETIRED;
	}

	/** 학생이 문항을 신고했다(R-03). 관리자가 검토할 때까지 배틀에서 빠진다. */
	public void markUnderReview() {
		this.status = QuestionStatus.UNDER_REVIEW;
	}


	/**
	 * 관리자 검수(X-05). VERIFIED·UNDER_REVIEW·RETIRED 끼리는 서로 바꿀 수 있고, UNJUDGED 에서는 VERIFIED(검수 통과)·RETIRED 로만 간다.
	 * UNJUDGED 로 되돌릴 수는 없다. 같은 상태로 바꾸면 검수자·시각만 새로 남는다.
	 */
	public void reviewByAdmin(QuestionStatus next, Long adminId, Instant now) {
		if (next == QuestionStatus.UNJUDGED) {
			throw new BusinessException(ErrorCode.CONFLICT, "검수 대기(UNJUDGED)로는 되돌릴 수 없습니다.");
		}
		if (status == QuestionStatus.UNJUDGED && next == QuestionStatus.UNDER_REVIEW) {
			throw new BusinessException(ErrorCode.CONFLICT, "검수 대기 문항은 통과(VERIFIED) 또는 폐기(RETIRED)로만 바꿀 수 있습니다.");
		}
		this.status = next;
		this.reviewedBy = adminId;
		this.reviewedAt = now;
	}

}
