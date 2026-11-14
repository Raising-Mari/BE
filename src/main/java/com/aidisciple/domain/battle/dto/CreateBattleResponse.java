package com.aidisciple.domain.battle.dto;

import java.time.Instant;

import com.aidisciple.domain.battle.entity.BattleStatus;

/** B-01 */
public record CreateBattleResponse(Long id, String roomCode, BattleStatus status, Instant expiresAt) {
}
