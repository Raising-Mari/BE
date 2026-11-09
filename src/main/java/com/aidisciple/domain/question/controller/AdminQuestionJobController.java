package com.aidisciple.domain.question.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.question.dto.AdminQuestionJobResponse;
import com.aidisciple.domain.question.dto.CreateQuestionJobRequest;
import com.aidisciple.domain.question.dto.CreateQuestionJobResponse;
import com.aidisciple.domain.question.service.AdminQuestionJobService;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

/** 9절 관리자·문항 생성 (WP12). ADMIN 만 — SecurityConfig 가 /api/admin/** 경로로 막는다. */
@Tag(name = "관리자·문항 생성")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminQuestionJobController {

	private final AdminQuestionJobService adminQuestionJobService;

	@Operation(summary = "X-01 문항 생성 작업 수동 실행 (targetCount 생략 시 설정값)")
	@PostMapping("/units/{unitId}/question-jobs")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CreateQuestionJobResponse create(
		@CurrentUser AuthUser user,
		@PathVariable Long unitId,
		@Valid @RequestBody(required = false) CreateQuestionJobRequest request
	) {
		Integer targetCount = request == null ? null : request.targetCount();
		return adminQuestionJobService.create(user.userId(), unitId, targetCount);
	}

	@Operation(summary = "X-02 작업 진행·단계별 탈락 수")
	@GetMapping("/question-jobs/{jobId}")
	public AdminQuestionJobResponse get(@PathVariable Long jobId) {
		return adminQuestionJobService.get(jobId);
	}

}
