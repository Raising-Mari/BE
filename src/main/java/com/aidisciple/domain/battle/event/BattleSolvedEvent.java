package com.aidisciple.domain.battle.event;

/**
 * 두 참가자(연습 배틀은 한 명)의 제자 풀이가 모두 끝나 답이 {@code PENDING} 으로 저장됐다 — 이제 채점할 차례다(WP9).
 * 트랜잭션 안에서 발행되므로 받는 쪽은 {@code @TransactionalEventListener(AFTER_COMMIT)} 를 쓴다.
 * 서버가 재시작되면 {@code BattleSolveRecovery} 가 채점 전인 배틀에 대해 이 이벤트를 다시 낸다.
 */
public record BattleSolvedEvent(long battleId) {
}
