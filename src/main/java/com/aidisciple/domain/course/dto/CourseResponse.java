package com.aidisciple.domain.course.dto;

/**
 * C-01 검색 결과의 항목, C-02 만들기, C-04 상세. 학과가 없는 과목(교양 등)은 {@code department} 가 null 이다.
 * {@code hasMyDisciple} 은 FE 가 "제자 만들기"와 "가르치러 가기" 버튼을 고르는 데 쓴다.
 */
public record CourseResponse(
	Long id,
	String name,
	String term,
	Department department,
	boolean hasMyDisciple
) {

	public record Department(Long id, String name) {
	}

}
