package com.aidisciple.domain.question.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.question.dto.BattleReadinessResponse;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

/** 5절 문제 은행 (WP7). 학생 API 는 이것 하나뿐이고 나머지는 관리자·내부 작업이다. */
@Tag(name = "문제 은행")
@RestController
@RequiredArgsConstructor
public class QuestionController {

	private final QuestionBankService questionBankService;

	@Operation(summary = "Q-01 이 주차로 배틀할 수 있는가 (나에게 안 나온 문항 수만, 내용 없음)")
	@GetMapping("/api/units/{unitId}/battle-readiness")
	public BattleReadinessResponse battleReadiness(
		@CurrentUser AuthUser user,
		@PathVariable Long unitId,
		@RequestParam(defaultValue = "5") @Min(3) @Max(10) int questionCount
	) {
		return questionBankService.readiness(user.userId(), unitId, questionCount);
	}

}
