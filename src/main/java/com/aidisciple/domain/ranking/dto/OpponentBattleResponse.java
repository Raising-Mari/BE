package com.aidisciple.domain.ranking.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.aidisciple.domain.battle.entity.BattleResult;

/** K-04 특정 상대와 한 랭킹 배틀 하나. {@code result} 는 내 쪽 승패다. api-spec 에 항목 모양이 없어 화면에 필요해 보이는 값으로 정했다. */
public record OpponentBattleResponse(
	Long battleId,
	Course course,
	BattleResult result,
	BigDecimal myScore,
	BigDecimal opponentScore,
	Instant finishedAt
) {

	public record Course(Long id, String name) {
	}

}
