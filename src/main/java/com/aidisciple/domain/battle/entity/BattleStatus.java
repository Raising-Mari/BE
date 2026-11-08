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

	/** 시작 전의 방. 입장 시간(expires_at)이 지나면 진행 중으로 치지 않는다. */
	public static final List<BattleStatus> OPEN_ROOM = List.of(WAITING, LOBBY);

	/** 시작한 뒤 아직 끝나지 않은 배틀. 진행 중 = OPEN_ROOM + STARTED */
	public static final List<BattleStatus> STARTED = List.of(RUNNING, ERROR);

	/** 방 코드로 입장·준비를 할 수 있는, 아직 시작 전인 상태 */
	public boolean isOpenRoom() {
		return this == WAITING || this == LOBBY;
	}

}
