package com.aidisciple.ai.log;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 9절 관리자·통계 (WP12). ADMIN 만 — SecurityConfig 가 /api/admin/** 경로로 막는다. */
@Tag(name = "관리자·통계")
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminAiCallStatsController {

	private final AiCallStatsService aiCallStatsService;

	@Operation(summary = "X-10 일·용도별 AI 호출 수 (days: 1~31, 기본 7, 한국 시간 기준)")
	@GetMapping("/ai-calls")
	public AiCallStatsResponse aiCalls(@RequestParam(required = false) Integer days) {
		return aiCallStatsService.daily(days);
	}

}
