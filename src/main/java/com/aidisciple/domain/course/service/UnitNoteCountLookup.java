package com.aidisciple.domain.course.service;

import java.util.Map;

/**
 * C-05 의 {@code myNoteCount}. 주차 목록은 course 도메인이 만들지만 노트는 teaching 도메인의 것이라, 인터페이스만 두고
 * 구현은 teaching 이 한다(course 가 teaching 을 import 하면 teaching → course 와 맞물려 순환이 생긴다).
 */
public interface UnitNoteCountLookup {

	/** 이 사용자가 이 과목에서 주차별로 가진 ACTIVE 노트 수. 노트가 없는 주차는 키가 없고, 제자가 없으면 빈 맵이다. */
	Map<Long, Long> activeNoteCounts(long userId, long courseId);

}
