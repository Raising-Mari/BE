package com.aidisciple.domain.ranking.dto;

import java.util.List;

/**
 * K-01 과목별 순위. RANKED 배틀만 세고 {@code gamesPlayed ≥ 1} 만 나온다. 동점이면 userId 가 작은 쪽이 앞이다.
 * 페이지 필드는 공통 PageResponse 와 같고, 내 순위({@code me})를 함께 준다 (배틀 전이면 null).
 */
public record RankingResponse(
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
		int draws
	) {
	}

}
