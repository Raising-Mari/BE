package com.aidisciple.domain.review.dto;

import java.time.Instant;

import com.aidisciple.domain.review.entity.ReportKind;
import com.aidisciple.domain.review.entity.ReportReasonType;
import com.aidisciple.domain.review.entity.ReportStatus;

/** X-07·X-08 관리자용 신고 한 건. 문항 본문을 함께 실어 목록에서 바로 판단할 수 있게 한다. */
public record AdminReportResponse(
	Long id,
	ReportKind kind,
	ReportReasonType reasonType,
	String detail,
	ReportStatus status,
	Long questionId,
	String questionStem,
	Long battleAnswerId,
	Long reporterId,
	String resolutionNote,
	Long handledBy,
	Instant handledAt,
	Instant createdAt
) {
}
