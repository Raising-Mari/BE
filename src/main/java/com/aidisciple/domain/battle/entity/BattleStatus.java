package com.aidisciple.domain.battle.entity;

/**
 * DB {@code battles.status}. 진행 중 = WAITING·LOBBY·RUNNING·ERROR.
 * 허용되는 전이는 api-spec 6절 끝의 "배틀 상태 전이"를 따른다.
 */
public enum BattleStatus {
	WAITING,
	LOBBY,
	RUNNING,
	FINISHED,
	CANCELED,
	EXPIRED,
	ERROR
}
