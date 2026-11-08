package com.aidisciple.domain.battle.service;

import java.util.Optional;

/**
 * 한 배틀이 한 참가자의 Elo 를 얼마나 바꿨는가. 배틀 응답(B-03 {@code eloDelta}, B-07 {@code elo})에 싣는다.
 * 배틀 도메인은 랭킹을 모르므로 인터페이스만 두고, 구현은 랭킹 도메인이 한다(의존 방향이 한쪽으로만 흐르게).
 * 랭킹 배틀이 아니거나 아직 반영 전이면 빈 값이다.
 */
public interface BattleEloLookup {

	Optional<EloChange> find(long battleId, long userId);

	record EloChange(int before, int after, int delta) {
	}

}
