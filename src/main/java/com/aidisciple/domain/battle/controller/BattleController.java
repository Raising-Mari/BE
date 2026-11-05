package com.aidisciple.domain.battle.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.battle.dto.BattleResponse;
import com.aidisciple.domain.battle.dto.BattleResultResponse;
import com.aidisciple.domain.battle.dto.BattleSummaryResponse;
import com.aidisciple.domain.battle.dto.CreateBattleRequest;
import com.aidisciple.domain.battle.dto.CreateBattleResponse;
import com.aidisciple.domain.battle.dto.JoinBattleRequest;
import com.aidisciple.domain.battle.dto.ReadyRequest;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.global.api.NotImplemented;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

/** 6절 배틀 (WP8, B-07 결과는 WP9). P1 인 B-08·B-09·B-10 은 이 스켈레톤에 넣지 않았다. */
@Tag(name = "배틀")
@RestController
@RequestMapping("/api/battles")
public class BattleController {

	@Operation(summary = "B-01 방 만들기 (RANKED)")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreateBattleResponse create(@CurrentUser AuthUser user, @Valid @RequestBody CreateBattleRequest request) {
		return NotImplemented.yet();
	}

	@Operation(summary = "B-02 방 코드로 입장")
	@PostMapping("/join")
	public BattleResponse join(@CurrentUser AuthUser user, @Valid @RequestBody JoinBattleRequest request) {
		return NotImplemented.yet();
	}

	@Operation(summary = "B-03 방·진행 상태 (2~3초 폴링)")
	@GetMapping("/{battleId}")
	public BattleResponse get(@CurrentUser AuthUser user, @PathVariable Long battleId) {
		return NotImplemented.yet();
	}

	@Operation(summary = "B-04 준비 완료 (누른 순간 내 노트 스냅샷 저장, 멱등)")
	@PostMapping("/{battleId}/ready")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void ready(
		@CurrentUser AuthUser user,
		@PathVariable Long battleId,
		@RequestBody(required = false) ReadyRequest request
	) {
		NotImplemented.yet();
	}

	@Operation(summary = "B-05 방 취소·나가기 (참가자 누구나, ERROR 에서는 포기)")
	@PostMapping("/{battleId}/cancel")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(@CurrentUser AuthUser user, @PathVariable Long battleId) {
		NotImplemented.yet();
	}

	@Operation(summary = "B-06 내 배틀 기록 (mode=PRACTICE 가 연습 배틀 기록)")
	@GetMapping
	public PageResponse<BattleSummaryResponse> myBattles(
		@CurrentUser AuthUser user,
		@RequestParam(required = false) BattleMode mode,
		@RequestParam(required = false) BattleStatus status,
		@RequestParam(required = false) Long courseId,
		@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size
	) {
		return NotImplemented.yet();
	}

	@Operation(summary = "B-07 결과 (끝난 배틀만)")
	@GetMapping("/{battleId}/result")
	public BattleResultResponse result(@CurrentUser AuthUser user, @PathVariable Long battleId) {
		return NotImplemented.yet();
	}

}
