package com.aidisciple.domain.course.dto;

/** C-03 내 과목 = 내 제자가 있는 과목. 목록에서 바로 가르치기로 들어갈 수 있게 제자를 함께 준다. */
public record MyCourseResponse(
	Long id,
	String name,
	String term,
	Disciple disciple
) {

	public record Disciple(Long id, String name, String characterKey) {
	}

}
