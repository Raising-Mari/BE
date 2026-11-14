package com.aidisciple.domain.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** R-07 노트 교환 리뷰. evidenceNoteEntryId 는 상대가 이 배틀에 쓴(스냅샷) 노트 중 하나만 고를 수 있다(선택). */
public record WriteNoteReviewRequest(
	@NotBlank @Size(max = 2000) String goodPoints,
	@NotBlank @Size(max = 2000) String suggestions,
	Long evidenceNoteEntryId
) {
}
