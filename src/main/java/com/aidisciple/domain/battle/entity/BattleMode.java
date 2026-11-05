package com.aidisciple.domain.battle.entity;

/** DB {@code battles.mode}. RANKED 는 둘이 겨루는 랭킹 반영 배틀, PRACTICE 는 혼자 하는 연습 배틀(Elo 미반영) */
public enum BattleMode {
	RANKED,
	PRACTICE
}
