package com.aidisciple.domain.ranking.dto;

import java.time.Instant;

/** K-03 상대별 전적 */
public record OpponentRecordResponse(
	Opponent opponent,
	int wins,
	int losses,
	int draws,
	Instant lastBattleAt
) {

	public record Opponent(Long id, String nickname) {
	}

}
