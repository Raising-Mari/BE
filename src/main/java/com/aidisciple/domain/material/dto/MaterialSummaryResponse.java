package com.aidisciple.domain.material.dto;

import java.time.Instant;
import java.util.List;

import com.aidisciple.domain.material.entity.MaterialStatus;

/** M-04 내가 올린 자료 목록의 항목 */
public record MaterialSummaryResponse(
	Long id,
	String title,
	MaterialStatus status,
	Instant createdAt,
	List<Long> unitIds
) {
}
