package com.aidisciple.domain.course.service;

import java.util.Collection;
import java.util.Set;

/** C-05 의 {@code battleReady}. 구현은 question 도메인이 한다({@link UnitNoteCountLookup} 와 같은 이유). */
public interface UnitBattleReadyLookup {

	/** 이 사용자에게 아직 안 나온, 배틀에 쓸 수 있는 문항이 {@code questionCount} 개 이상인 주차 */
	Set<Long> battleReadyUnits(long userId, Collection<Long> unitIds, int questionCount);

}
