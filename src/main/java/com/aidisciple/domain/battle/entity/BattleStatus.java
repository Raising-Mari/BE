package com.aidisciple.domain.battle.entity;

import java.util.List;

/**
 * DB {@code battles.status}. 진행 중 = WAITING·LOBBY·RUNNING·ERROR.
 * 허용되는 전이는 api-spec 6절 끝의 "배틀 상태 전이"를 따르고, {@link Battle} 의 전이 메서드가 지킨다.
 */
public enum BattleStatus {
	WAITING,
	LOBBY,
	RUNNING,
	FINISHED,
	CANCELED,
	EXPIRED,
	ERROR;

	/** 같은 사용자가 동시에 하나만 가질 수 있는 "진행 중" 상태 */
	public static final List<BattleStatus> IN_PROGRESS = List.of(WAITING, LOBBY, RUNNING, ERROR);

	/** 방 코드로 입장·준비를 할 수 있는, 아직 시작 전인 상태 */
	public boolean isOpenRoom() {
		return this == WAITING || this == LOBBY;
	}

}
