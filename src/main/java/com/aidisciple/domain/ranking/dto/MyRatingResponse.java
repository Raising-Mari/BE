package com.aidisciple.domain.ranking.dto;

/** K-02 내 과목별 Elo. api-spec 에 항목 모양이 없어 K-01 항목에 맞춰 정했다. */
public record MyRatingResponse(
	Course course,
	int rating,
	int gamesPlayed,
	int wins,
	int losses,
	int draws
) {

	public record Course(Long id, String name) {
	}

}
