package com.aidisciple.domain.battle.event;

import com.aidisciple.domain.battle.entity.BattleMode;

/**
 * 채점이 끝나 배틀이 {@code FINISHED} 가 됐다. 랭킹이 {@code RANKED} 배틀의 Elo 를 반영하는 신호다 — 연습 배틀은 반영하지 않는다.
 * 정산 트랜잭션 안에서 발행되며, 랭킹은 평범한 {@code @EventListener} 로 <b>같은 트랜잭션</b>에서 받는다
 * ("FINISHED 이면 Elo 도 반영돼 있다"를 지키려고). 받는 쪽이 실패하면 정산이 통째로 되돌려진다.
 */
public record BattleFinishedEvent(long battleId, BattleMode mode) {
}
