package com.aidisciple.domain.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.aidisciple.domain.review.entity.ReportStatus;

/**
 * X-08 신고 처리. {@code questionAction} 은 생략하면 NONE.
 * RETIRE = 문항 폐기, RESTORE = 신고로 빠졌던 문항을 검수 통과(VERIFIED)로 되돌린다.
 */
public record HandleReportRequest(
	@NotNull ReportStatus status,
	@Size(max = 2000) String resolutionNote,
	QuestionAction questionAction
) {

	public enum QuestionAction {
		RETIRE,
		RESTORE,
		NONE
	}

}
