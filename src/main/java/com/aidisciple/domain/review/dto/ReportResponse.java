package com.aidisciple.domain.review.dto;

import java.time.Instant;

import com.aidisciple.domain.review.entity.ReportKind;
import com.aidisciple.domain.review.entity.ReportStatus;

/** R-03 접수 결과 */
public record ReportResponse(Long id, ReportKind kind, ReportStatus status, Instant createdAt) {
}
