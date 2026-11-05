package com.aidisciple.domain.course.dto;

/**
 * C-05 주차(단원) 목록의 항목, C-06 만들기. 주차의 문항 수·내용은 내려주지 않고 {@code battleReady} 만 준다.
 * {@code myNoteCount} 가 0 이면 이 주차로 방을 만들거나 들어갈 수 없다 (BATTLE_NOTES_REQUIRED).
 */
public record UnitResponse(
	Long id,
	String title,
	long materialCount,
	long myNoteCount,
	boolean battleReady
) {
}
