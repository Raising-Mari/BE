package com.aidisciple.domain.battle.event;

import com.aidisciple.domain.battle.entity.BattleMode;

/**
 * 채점이 끝나 배틀이 {@code FINISHED} 가 됐다. 랭킹(WP10)이 {@code RANKED} 배틀의 Elo 를 반영하는 신호다 — 연습 배틀은 반영하지 않는다.
 * 트랜잭션 안에서 발행되므로 받는 쪽은 {@code @TransactionalEventListener(AFTER_COMMIT)} 를 쓴다.
 */
public record BattleFinishedEvent(long battleId, BattleMode mode) {
}
