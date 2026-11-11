package com.aidisciple.domain.review.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.aidisciple.global.jpa.BaseTimeEntity;

/**
 * 노트 교환 리뷰(R-07). 배틀당 한 사람이 한 번만 쓴다 — DB UNIQUE(battle, reviewer)와 서비스 검사가 함께 막는다.
 * 읽음·반영 표시({@code readAt}·{@code appliedAt})는 R-09(P2)용 칸이라 지금은 비어 있다.
 */
@Getter
@Entity
@Table(name = "note_reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteReview extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long battleId;

	@Column(nullable = false)
	private Long reviewerId;

	@Column(nullable = false)
	private Long targetUserId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String goodPoints;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String suggestions;

	private Long evidenceNoteEntryId;

	private Instant readAt;

	private Instant appliedAt;

	public static NoteReview write(Long battleId, Long reviewerId, Long targetUserId, String goodPoints,
								   String suggestions, Long evidenceNoteEntryId) {
		NoteReview review = new NoteReview();
		review.battleId = battleId;
		review.reviewerId = reviewerId;
		review.targetUserId = targetUserId;
		review.goodPoints = goodPoints;
		review.suggestions = suggestions;
		review.evidenceNoteEntryId = evidenceNoteEntryId;
		return review;
	}

}
