package com.aidisciple.domain.review.entity;

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
 * 문항 신고(QUESTION)·채점 이의제기(GRADING). 학생은 접수만 하고(R-03), 처리(상태·처리 메모)는 관리자(X-08, WP12)가 한다.
 * 같은 답에 같은 종류는 한 번만 — DB UNIQUE(reporter, battle_answer, kind)와 서비스 검사가 함께 막는다.
 */
@Getter
@Entity
@Table(name = "reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long reporterId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private ReportKind kind;

	@Column(nullable = false)
	private Long questionId;

	private Long battleAnswerId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ReportReasonType reasonType;

	@Column(columnDefinition = "TEXT")
	private String detail;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 15)
	private ReportStatus status;

	@Column(columnDefinition = "TEXT")
	private String resolutionNote;

	private Long handledBy;

	private Instant handledAt;

	/** 학생이 배틀 답에서 신고했다 (R-03) */
	public static Report ofAnswer(Long reporterId, ReportKind kind, Long questionId, Long battleAnswerId,
								  ReportReasonType reasonType, String detail) {
		Report report = new Report();
		report.reporterId = reporterId;
		report.kind = kind;
		report.questionId = questionId;
		report.battleAnswerId = battleAnswerId;
		report.reasonType = reasonType;
		report.detail = detail;
		report.status = ReportStatus.OPEN;
		return report;
	}


	/** X-08 관리자 처리. 다시 처리하면 마지막 처리로 덮어쓴다. */
	public void handle(ReportStatus status, String resolutionNote, Long adminId, Instant now) {
		this.status = status;
		this.resolutionNote = resolutionNote;
		this.handledBy = adminId;
		this.handledAt = now;
	}

}
