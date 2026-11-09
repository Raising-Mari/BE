package com.aidisciple.domain.review.entity;

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
 * 답 하나에 대한 내 복기(R-02). 답마다 한 줄이고(DB UNIQUE), 다시 저장하면 같은 줄을 고친다.
 * 원인은 학생이 고른 최종 원인이다 — 서버의 원인 제안({@code battle_answers.cause_suggestion})과 따로 둔다.
 * 시험 사용에서 학습 효과를 확인하는 자료라 지우지 않는다.
 */
@Getter
@Entity
@Table(name = "review_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewEntry extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long battleAnswerId;

	@Column(nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ReviewCause userCause;

	@Column(columnDefinition = "TEXT")
	private String studyMemo;

	@Column(columnDefinition = "TEXT")
	private String aiMemo;

	@Column(columnDefinition = "TEXT")
	private String sourceNote;

	@Column(columnDefinition = "TEXT")
	private String reasonNote;

	public static ReviewEntry create(Long battleAnswerId, Long userId, ReviewCause userCause, String studyMemo,
									 String aiMemo, String sourceNote, String reasonNote) {
		ReviewEntry entry = new ReviewEntry();
		entry.battleAnswerId = battleAnswerId;
		entry.userId = userId;
		entry.update(userCause, studyMemo, aiMemo, sourceNote, reasonNote);
		return entry;
	}

	/** 다시 저장하면 전부 덮어쓴다 (빈 칸으로 보내면 그 메모를 지운 것이다) */
	public void update(ReviewCause userCause, String studyMemo, String aiMemo, String sourceNote, String reasonNote) {
		this.userCause = userCause;
		this.studyMemo = studyMemo;
		this.aiMemo = aiMemo;
		this.sourceNote = sourceNote;
		this.reasonNote = reasonNote;
	}

}
