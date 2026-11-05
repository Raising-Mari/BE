package com.aidisciple.domain.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.aidisciple.domain.review.entity.ReportKind;
import com.aidisciple.domain.review.entity.ReportReasonType;

/** R-03 문항 신고(QUESTION → 문항이 즉시 UNDER_REVIEW) 또는 채점 이의제기(GRADING) */
public record CreateReportRequest(
	@NotNull ReportKind kind,
	@NotNull ReportReasonType reasonType,
	@Size(max = 2000) String detail
) {
}
