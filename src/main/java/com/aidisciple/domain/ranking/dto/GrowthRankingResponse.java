package com.aidisciple.domain.ranking.dto;

import java.util.List;

/**
 * K-05 성장폭 순위. K-01 과 같은 모양에 기간 안의 Elo 증감({@code delta}) 기준으로 정렬한다 — 순위가 낮아도 많이 성장한 학생이
 * 보상받게 한다. 동점이면 userId 가 작은 쪽이 앞이다. 내 순위({@code me})는 그 기간에 배틀이 없었으면 null 이다.
 */
public record GrowthRankingResponse(
	List<Entry> items,
	Entry me,
	int page,
	int size,
	long totalElements,
	boolean hasNext
) {

	public record Entry(
		int rank,
		Long userId,
		String nickname,
		String departmentName,
		int rating,
		int gamesPlayed,
		int wins,
		int losses,
		int draws,
		int delta
	) {
	}

}
