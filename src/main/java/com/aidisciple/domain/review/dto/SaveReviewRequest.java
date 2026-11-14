package com.aidisciple.domain.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.aidisciple.domain.review.entity.ReviewCause;

/**
 * R-02 원인·메모 저장(있으면 수정). 원인만 필수다.
 * {@code sourceNote}·{@code reasonNote}(보완한 내용의 출처와 고른 이유)가 정보수집 역량의 증빙이 된다.
 */
public record SaveReviewRequest(
	@NotNull ReviewCause userCause,
	@Size(max = 2000) String studyMemo,
	@Size(max = 2000) String aiMemo,
	@Size(max = 2000) String sourceNote,
	@Size(max = 2000) String reasonNote
) {
}
