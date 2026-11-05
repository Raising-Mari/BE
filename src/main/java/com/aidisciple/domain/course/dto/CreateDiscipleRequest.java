package com.aidisciple.domain.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** D-01. {@code characterKey} 는 FE 에 있는 캐릭터 그림 식별자이고 생략할 수 있다. */
public record CreateDiscipleRequest(
	@NotBlank @Size(max = 30) String name,
	@Size(max = 30) String characterKey
) {
}
