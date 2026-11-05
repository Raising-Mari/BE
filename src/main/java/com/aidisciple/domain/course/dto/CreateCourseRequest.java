package com.aidisciple.domain.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** C-02. 학교는 로그인한 사용자의 학교로 정하므로 받지 않는다. {@code term} 예: {@code 2026-2} */
public record CreateCourseRequest(
	@NotBlank @Size(max = 100) String name,
	Long departmentId,
	@NotBlank @Size(max = 10) String term
) {
}
