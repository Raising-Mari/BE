package com.aidisciple.domain.battle.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.aidisciple.domain.battle.entity.BattleResult;

/**
 * 채점이 끝난 랭킹 배틀의 결과. 랭킹(Elo 반영)이 읽는 값이다 — 학생에게 내려가는 응답이 아니다.
 * 참가자는 정확히 둘이고 둘 다 승패가 정해져 있을 때만 이 값이 만들어진다.
 */
public record FinishedRankedBattle(long battleId, long courseId, Instant finishedAt, List<Participant> participants) {

	public record Participant(long userId, BattleResult result, BigDecimal totalScore) {
	}

}
