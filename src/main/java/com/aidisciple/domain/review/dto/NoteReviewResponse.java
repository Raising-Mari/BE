package com.aidisciple.domain.review.dto;

import java.time.Instant;

/** R-07·R-08 리뷰 한 건. evidenceNote 는 근거로 고른 상대 노트(없으면 null) */
public record NoteReviewResponse(
	long id,
	long battleId,
	Person reviewer,
	Person target,
	String goodPoints,
	String suggestions,
	EvidenceNote evidenceNote,
	Instant createdAt
) {

	public record Person(long userId, String nickname) {
	}

	public record EvidenceNote(long id, String content) {
	}

}
