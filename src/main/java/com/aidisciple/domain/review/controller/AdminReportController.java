package com.aidisciple.domain.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.review.dto.AdminReportResponse;
import com.aidisciple.domain.review.dto.HandleReportRequest;
import com.aidisciple.domain.review.entity.ReportStatus;
import com.aidisciple.domain.review.service.AdminReportService;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

/** 9절 관리자·신고 (WP12). ADMIN 만 — SecurityConfig 가 /api/admin/** 경로로 막는다. */
@Tag(name = "관리자·신고")
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

	private final AdminReportService adminReportService;

	@Operation(summary = "X-07 신고·이의제기 목록 (status)")
	@GetMapping
	public PageResponse<AdminReportResponse> list(
		@RequestParam(required = false) ReportStatus status,
		@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size
	) {
		return adminReportService.list(status, page, size);
	}

	@Operation(summary = "X-08 신고 처리 (status, resolutionNote, questionAction: RETIRE·RESTORE·NONE)")
	@PatchMapping("/{reportId}")
	public AdminReportResponse handle(
		@CurrentUser AuthUser user,
		@PathVariable Long reportId,
		@Valid @RequestBody HandleReportRequest request
	) {
		return adminReportService.handle(user.userId(), reportId, request);
	}

}
