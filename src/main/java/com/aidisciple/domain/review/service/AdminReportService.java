package com.aidisciple.domain.review.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.service.AdminQuestionService;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.domain.review.dto.AdminReportResponse;
import com.aidisciple.domain.review.dto.HandleReportRequest;
import com.aidisciple.domain.review.entity.Report;
import com.aidisciple.domain.review.entity.ReportStatus;
import com.aidisciple.domain.review.repository.ReportRepository;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.api.Pages;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 관리자 신고 처리 X-07·X-08. 권한은 SecurityConfig 가 /api/admin/** 경로로 이미 막는다.
 * 문항 상태는 문제 은행의 {@link AdminQuestionService} 로만 바꾼다(검수자·시각이 함께 남는다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {

	private final ReportRepository reportRepository;
	private final QuestionBankService questionBankService;
	private final AdminQuestionService adminQuestionService;
	private final Clock clock;

	/** X-07: 최신순. status 로 거른다(OPEN 이면 처리 대기). */
	public PageResponse<AdminReportResponse> list(ReportStatus status, Integer page, Integer size) {
		Page<Report> found = reportRepository.search(status, Pages.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
		Map<Long, String> stems = questionBankService.stems(
			found.getContent().stream().map(Report::getQuestionId).distinct().toList());
		return PageResponse.from(found, report -> toResponse(report, stems.get(report.getQuestionId())));
	}

	/** X-08: 처리 상태·메모를 남기고, 필요하면 문항을 폐기하거나 되살린다. OPEN 으로는 되돌릴 수 없다. */
	@Transactional
	public AdminReportResponse handle(long adminId, long reportId, HandleReportRequest request) {
		if (request.status() == ReportStatus.OPEN) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "처리 상태는 REVIEWING, ACCEPTED, REJECTED 중 하나입니다.");
		}
		Report report = reportRepository.findById(reportId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		String note = request.resolutionNote() == null || request.resolutionNote().isBlank()
			? null : request.resolutionNote().strip();
		report.handle(request.status(), note, adminId, clock.instant());

		HandleReportRequest.QuestionAction action = request.questionAction() == null
			? HandleReportRequest.QuestionAction.NONE : request.questionAction();
		switch (action) {
			case RETIRE -> adminQuestionService.changeStatus(adminId, report.getQuestionId(), QuestionStatus.RETIRED);
			case RESTORE -> adminQuestionService.changeStatus(adminId, report.getQuestionId(), QuestionStatus.VERIFIED);
			case NONE -> {
			}
		}
		String stem = questionBankService.stems(List.of(report.getQuestionId())).get(report.getQuestionId());
		return toResponse(report, stem);
	}

	private static AdminReportResponse toResponse(Report report, String questionStem) {
		return new AdminReportResponse(report.getId(), report.getKind(), report.getReasonType(), report.getDetail(),
			report.getStatus(), report.getQuestionId(), questionStem, report.getBattleAnswerId(), report.getReporterId(),
			report.getResolutionNote(), report.getHandledBy(), report.getHandledAt(), report.getCreatedAt());
	}

}
