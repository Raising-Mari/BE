package com.aidisciple.domain.course.dto;

import java.time.Instant;

/** D-01 만들기, D-02 내 제자 */
public record DiscipleResponse(
	Long id,
	Long courseId,
	String name,
	String characterKey,
	Instant createdAt
) {
}
