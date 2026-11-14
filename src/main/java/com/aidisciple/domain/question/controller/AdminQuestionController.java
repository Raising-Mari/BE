package com.aidisciple.domain.question.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.question.dto.AdminQuestionDetail;
import com.aidisciple.domain.question.dto.AdminQuestionSummary;
import com.aidisciple.domain.question.dto.ChangeQuestionStatusRequest;
import com.aidisciple.domain.question.dto.CreateManualQuestionRequest;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.service.AdminQuestionService;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

/** 9절 관리자·문항 (WP12). ADMIN 만 — SecurityConfig 가 /api/admin/** 경로로 막는다. */
@Tag(name = "관리자·문항")
@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {

	private final AdminQuestionService adminQuestionService;

	@Operation(summary = "X-03 문항 목록 (unitId·status, status=UNJUDGED 면 검수 대기) + 사용 횟수·정답률")
	@GetMapping
	public PageResponse<AdminQuestionSummary> list(
		@RequestParam(required = false) Long unitId,
		@RequestParam(required = false) QuestionStatus status,
		@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size
	) {
		return adminQuestionService.list(unitId, status, page, size);
	}

	@Operation(summary = "X-06 문항 직접 추가 (source=MANUAL, 바로 VERIFIED)")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AdminQuestionDetail create(@CurrentUser AuthUser user, @Valid @RequestBody CreateManualQuestionRequest request) {
		return adminQuestionService.create(user.userId(), request);
	}

	@Operation(summary = "X-04 문항 상세 + 검증 로그")
	@GetMapping("/{questionId}")
	public AdminQuestionDetail detail(@PathVariable Long questionId) {
		return adminQuestionService.detail(questionId);
	}

	@Operation(summary = "X-05 문항 상태 변경 (UNJUDGED → VERIFIED·RETIRED, VERIFIED↔UNDER_REVIEW↔RETIRED)")
	@PatchMapping("/{questionId}/status")
	public AdminQuestionDetail changeStatus(
		@CurrentUser AuthUser user,
		@PathVariable Long questionId,
		@Valid @RequestBody ChangeQuestionStatusRequest request
	) {
		return adminQuestionService.changeStatus(user.userId(), questionId, request.status());
	}

}
