package com.aidisciple.domain.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** C-06. 예: {@code 3주차} */
public record CreateUnitRequest(@NotBlank @Size(max = 100) String title) {
}
