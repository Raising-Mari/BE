package com.aidisciple.domain.battle.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * B-10. {@code battleAnswerIds} 를 생략(null)하면 원본 배틀에서 내가 맞히지 못한 답 전부가 대상이다.
 * 주면 그 답들만이고, 맞힌 답도 고를 수 있다. 빈 목록은 "아무것도 고르지 않음"이다.
 */
public record CreatePracticeRequest(
	@NotNull Long sourceBattleId,
	@Size(max = 10) List<@NotNull Long> battleAnswerIds
) {
}
