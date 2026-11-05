package com.aidisciple.domain.battle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** B-01 RANKED 방 만들기. {@code questionCount} 를 생략하면 5 로 본다. */
public record CreateBattleRequest(
	@NotNull Long courseId,
	@NotNull Long unitId,
	@Min(3) @Max(10) Integer questionCount
) {

	public static final int DEFAULT_QUESTION_COUNT = 5;

	public int questionCountOrDefault() {
		return questionCount == null ? DEFAULT_QUESTION_COUNT : questionCount;
	}

}
