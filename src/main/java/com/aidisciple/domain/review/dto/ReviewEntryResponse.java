package com.aidisciple.domain.review.dto;

import com.aidisciple.domain.review.entity.ReviewCause;

/** R-02 저장 결과. R-01 항목의 {@code myReview} 와 같은 값을 돌려준다. */
public record ReviewEntryResponse(
	Long battleAnswerId,
	ReviewCause userCause,
	String studyMemo,
	String aiMemo,
	String sourceNote,
	String reasonNote
) {
}
