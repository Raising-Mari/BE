package com.aidisciple.domain.review.controller;

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

import com.aidisciple.domain.review.dto.NoteReviewResponse;
import com.aidisciple.domain.review.dto.NoteReviewsResponse;
import com.aidisciple.domain.review.dto.OpponentNotesResponse;
import com.aidisciple.domain.review.dto.WriteNoteReviewRequest;
import com.aidisciple.domain.review.service.NoteReviewService;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

/** 7절 노트 교환 리뷰 (WP11 P1). 끝난 RANKED 배틀의 참가자만, 노트·리뷰 쓰기는 두 사람 모두 공개 동의 시. */
@Tag(name = "복기·노트 교환 리뷰")
@RestController
@RequestMapping("/api/battles/{battleId}")
@RequiredArgsConstructor
public class NoteReviewController {

	private final NoteReviewService noteReviewService;

	@Operation(summary = "R-06 상대 노트 (두 사람 모두 공개 동의 시, 준비 순간 스냅샷)")
	@GetMapping("/opponent-notes")
	public OpponentNotesResponse opponentNotes(@CurrentUser AuthUser user, @PathVariable Long battleId) {
		return noteReviewService.opponentNotes(user.userId(), battleId);
	}

	@Operation(summary = "R-07 노트 교환 리뷰 작성 (배틀당 1회)")
	@PostMapping("/note-reviews")
	@ResponseStatus(HttpStatus.CREATED)
	public NoteReviewResponse write(
		@CurrentUser AuthUser user,
		@PathVariable Long battleId,
		@Valid @RequestBody WriteNoteReviewRequest request
	) {
		return noteReviewService.write(user.userId(), battleId, request);
	}

	@Operation(summary = "R-08 내가 쓴 / 받은 리뷰")
	@GetMapping("/note-reviews")
	public NoteReviewsResponse list(@CurrentUser AuthUser user, @PathVariable Long battleId) {
		return noteReviewService.list(user.userId(), battleId);
	}

}
