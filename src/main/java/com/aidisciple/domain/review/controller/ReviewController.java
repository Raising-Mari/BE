package com.aidisciple.domain.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.review.dto.CreateReportRequest;
import com.aidisciple.domain.review.dto.ReportResponse;
import com.aidisciple.domain.review.dto.RevealAnswersResponse;
import com.aidisciple.domain.review.dto.ReviewEntryResponse;
import com.aidisciple.domain.review.dto.ReviewResponse;
import com.aidisciple.domain.review.dto.SaveReviewRequest;
import com.aidisciple.domain.review.service.ReviewService;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

/** 7절 복기·신고 (WP11). P1 인 노트 교환 리뷰(R-06~R-08)·복기 모아 보기(R-10)는 아직 없다. */
@Tag(name = "복기·신고")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;

	@Operation(summary = "R-01 복기 화면 데이터 (정답·자료 근거는 정답 보기 전까지 숨김)")
	@GetMapping("/battles/{battleId}/review")
	public ReviewResponse review(@CurrentUser AuthUser user, @PathVariable Long battleId) {
		return reviewService.review(user.userId(), battleId);
	}

	@Operation(summary = "R-02 원인·메모 저장 (있으면 수정)")
	@PutMapping("/battle-answers/{answerId}/review")
	public ReviewEntryResponse saveReview(
		@CurrentUser AuthUser user,
		@PathVariable Long answerId,
		@Valid @RequestBody SaveReviewRequest request
	) {
		return reviewService.saveReview(user.userId(), answerId, request);
	}

	@Operation(summary = "R-03 문항 신고·채점 이의제기")
	@PostMapping("/battle-answers/{answerId}/reports")
	@ResponseStatus(HttpStatus.CREATED)
	public ReportResponse report(
		@CurrentUser AuthUser user,
		@PathVariable Long answerId,
		@Valid @RequestBody CreateReportRequest request
	) {
		return reviewService.report(user.userId(), answerId, request);
	}

	@Operation(summary = "R-11 정답 보기 (되돌릴 수 없음, 이 배틀의 연습이 끝난다)")
	@PostMapping("/battles/{battleId}/reveal-answers")
	public RevealAnswersResponse revealAnswers(@CurrentUser AuthUser user, @PathVariable Long battleId) {
		return reviewService.revealAnswers(user.userId(), battleId);
	}

}
