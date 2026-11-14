package com.aidisciple.domain.review.dto;

/** R-08 이 배틀에서 내가 쓴 리뷰와 받은 리뷰. 아직 없으면 null */
public record NoteReviewsResponse(NoteReviewResponse written, NoteReviewResponse received) {
}
