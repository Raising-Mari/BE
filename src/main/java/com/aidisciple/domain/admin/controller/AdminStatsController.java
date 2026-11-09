package com.aidisciple.domain.admin.controller;

import java.io.IOException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.admin.dto.QuestionAccuracyResponse;
import com.aidisciple.domain.admin.dto.UsageStatsResponse;
import com.aidisciple.domain.admin.service.AdminStatsService;
import com.aidisciple.domain.admin.service.BattleAnswerCsvExporter;

/** 9절 관리자·통계·내보내기 (WP12). ADMIN 만 — SecurityConfig 가 /api/admin/** 경로로 막는다. */
@Tag(name = "관리자·통계")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminStatsController {

	private final AdminStatsService adminStatsService;
	private final BattleAnswerCsvExporter csvExporter;

	@Operation(summary = "X-09 정답률이 0%·100%에 가까운 문항 (랭킹 배틀, 채점된 답 minAnswers 개 이상, 기본 5)")
	@GetMapping("/stats/questions")
	public QuestionAccuracyResponse questionAccuracy(
		@RequestParam(required = false) Long unitId,
		@RequestParam(required = false) Integer minAnswers
	) {
		return adminStatsService.questionAccuracy(unitId, minAnswers);
	}

	@Operation(summary = "X-11 시험 사용 지표 (days: 1~90, 기본 7, 한국 시간 기준)")
	@GetMapping("/stats/usage")
	public UsageStatsResponse usage(@RequestParam(required = false) Integer days) {
		return adminStatsService.usage(days);
	}

	@Operation(summary = "X-12 채점 일치도 비교용 CSV (days: 1~365, 기본 30, courseId 선택)")
	@GetMapping("/exports/battle-answers.csv")
	public void exportBattleAnswers(
		@RequestParam(required = false) Integer days,
		@RequestParam(required = false) Long courseId,
		HttpServletResponse response
	) throws IOException {
		int dayCount = BattleAnswerCsvExporter.resolveDays(days);
		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"battle-answers.csv\"");
		csvExporter.write(response.getWriter(), dayCount, courseId);
	}

}
