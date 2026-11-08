package com.aidisciple.domain.question.entity;

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

import com.aidisciple.global.jpa.BaseTimeEntity;

/**
 * 주차 하나의 문항 생성 작업(J-02). 상태를 DB 에 남겨 서버가 재시작돼도 RUNNING 작업을 다시 걸 수 있다.
 * 생성·통과·탈락 개수는 "생성 N개 중 몇 개가 어느 단계에서 탈락했나" 발표 자료의 바탕이 된다.
 */
@Getter
@Entity
@Table(name = "question_generation_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionGenerationJob extends BaseTimeEntity {

	private static final int FAIL_REASON_MAX = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long unitId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private JobTriggerType triggerType;

	private Long requestedBy;

	@Column(nullable = false)
	private int targetCount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private JobStatus status;

	@Column(nullable = false)
	private int generatedCount;

	@Column(nullable = false)
	private int passedCount;

	@Column(nullable = false)
	private int rejectedCount;

	@Column(length = FAIL_REASON_MAX)
	private String failReason;

	private Instant startedAt;

	private Instant finishedAt;

	public static QuestionGenerationJob queue(Long unitId, JobTriggerType triggerType, Long requestedBy,
											  int targetCount) {
		QuestionGenerationJob job = new QuestionGenerationJob();
		job.unitId = unitId;
		job.triggerType = triggerType;
		job.requestedBy = requestedBy;
		job.targetCount = targetCount;
		job.status = JobStatus.QUEUED;
		return job;
	}

	public void start(Instant now) {
		this.status = JobStatus.RUNNING;
		this.startedAt = now;
	}

	public void finish(Instant now, int generated, int passed, int rejected) {
		this.status = JobStatus.DONE;
		this.finishedAt = now;
		this.generatedCount = generated;
		this.passedCount = passed;
		this.rejectedCount = rejected;
	}

	/** 실패 이유는 오류 코드나 예외 클래스 이름만 남긴다 (자료 내용이 섞이지 않게) */
	public void fail(Instant now, String reason) {
		this.status = JobStatus.FAILED;
		this.finishedAt = now;
		this.failReason = reason == null || reason.length() <= FAIL_REASON_MAX ? reason
			: reason.substring(0, FAIL_REASON_MAX);
	}

}
