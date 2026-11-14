package com.aidisciple.domain.battle.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.aidisciple.domain.battle.entity.BattleResult;

/** 내가 한 끝난 랭킹 배틀 하나. 상대별 전적(K-03)과 상대와의 배틀 목록(K-04)이 읽는다. {@code result} 는 내 쪽 승패다. */
public record RankedBattleRecord(long battleId, long courseId, Instant finishedAt, BattleResult result, BigDecimal myScore,
								 long opponentId, BigDecimal opponentScore) {
}
