package com.aidisciple.domain.review.dto;

import java.time.Instant;

/** R-11 정답 보기. 되돌릴 수 없고 멱등이다 (이미 열었으면 같은 응답). */
public record RevealAnswersResponse(boolean answersRevealed, Instant revealedAt) {
}
