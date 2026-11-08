package com.aidisciple.domain.battle.service;

import java.util.Map;

import com.aidisciple.domain.course.dto.UnitRef;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/** 여러 배틀 서비스가 같은 모양으로 던지는 오류 */
final class BattleErrors {

	private BattleErrors() {
	}

	/**
	 * 그 주차에 ACTIVE 노트가 없다. FE 가 가르치기 화면으로 안내할 수 있게 주차와 과목 id 를 본문에 싣는다.
	 * (명세는 입장(B-02)에만 요구하지만 방 만들기·준비도 같은 값을 주는 편이 FE 가 한 가지로 처리하기 쉽다.)
	 */
	static BusinessException notesRequired(UnitRef unit) {
		return new BusinessException(ErrorCode.BATTLE_NOTES_REQUIRED, Map.of(
			"courseId", unit.courseId(),
			"unit", Map.of("id", unit.id(), "title", unit.title())));
	}

}
