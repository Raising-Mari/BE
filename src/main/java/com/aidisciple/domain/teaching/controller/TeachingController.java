package com.aidisciple.domain.teaching.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.teaching.dto.ChatMessageResponse;
import com.aidisciple.domain.teaching.dto.NotesResponse;
import com.aidisciple.domain.teaching.dto.SendMessageRequest;
import com.aidisciple.domain.teaching.dto.TeachingTurnResponse;
import com.aidisciple.domain.teaching.service.TeachingQueryService;
import com.aidisciple.domain.teaching.service.TeachingService;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

/** 4절 가르치기 (WP6). 노트를 직접 고치거나 지우는 API 는 만들지 않는다 — 고치려면 대화로 다시 말한다. */
@Tag(name = "가르치기")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/disciples/{discipleId}")
public class TeachingController {

	private final TeachingService teachingService;
	private final TeachingQueryService teachingQueryService;

	@Operation(summary = "T-01 학생 메시지 전송 → 제자 답 + 노트 변경")
	@PostMapping("/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public TeachingTurnResponse sendMessage(
		@CurrentUser AuthUser user,
		@PathVariable Long discipleId,
		@Valid @RequestBody SendMessageRequest request
	) {
		return teachingService.send(user.userId(), discipleId, request);
	}

	@Operation(summary = "T-02 대화 내역 (최신 먼저)")
	@GetMapping("/messages")
	public PageResponse<ChatMessageResponse> messages(
		@CurrentUser AuthUser user,
		@PathVariable Long discipleId,
		@RequestParam(required = false) Long unitId,
		@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size
	) {
		return teachingQueryService.messages(user.userId(), discipleId, unitId, page, size);
	}

	@Operation(summary = "T-03 현재 노트 (주차별로 묶음, unitId 는 선택)")
	@GetMapping("/notes")
	public NotesResponse notes(
		@CurrentUser AuthUser user,
		@PathVariable Long discipleId,
		@RequestParam(required = false) Long unitId
	) {
		return teachingQueryService.notes(user.userId(), discipleId, unitId);
	}

}
