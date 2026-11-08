package com.aidisciple.domain.ranking.dto;

import java.time.Instant;

/** K-07 내 Elo 변동 이력의 항목(api-spec 8절). 랭킹 화면의 "점수 변화"다. */
public record EloHistoryResponse(
	Long battleId,
	Opponent opponent,
	int ratingBefore,
	int ratingAfter,
	int delta,
	Instant createdAt
) {

	public record Opponent(Long id, String nickname) {
	}

}
