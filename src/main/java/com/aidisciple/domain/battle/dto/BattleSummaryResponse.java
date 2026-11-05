package com.aidisciple.domain.battle.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleResult;
import com.aidisciple.domain.battle.entity.BattleStatus;

/**
 * B-06 내 배틀 기록의 항목. 끝나지 않았으면 점수·결과가 null, 연습 배틀은 {@code opponent}·{@code result} 가 null 이다.
 * api-spec 에 항목 모양이 없어 화면에 필요해 보이는 값으로 정했다 — FE 와 맞춰 바꿀 수 있다.
 */
public record BattleSummaryResponse(
	Long id,
	BattleMode mode,
	BattleStatus status,
	Course course,
	Unit unit,
	Opponent opponent,
	BigDecimal myScore,
	BattleResult result,
	Instant createdAt
) {

	public record Course(Long id, String name) {
	}

	public record Unit(Long id, String title) {
	}

	public record Opponent(Long id, String nickname) {
	}

}
