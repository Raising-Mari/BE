package com.aidisciple.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.auth.dto.MeResponse;
import com.aidisciple.domain.auth.dto.UpdateMeRequest;
import com.aidisciple.domain.auth.service.MeService;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

@Tag(name = "인증")
@RestController
@RequiredArgsConstructor
public class MeController {

	private final MeService meService;

	@Operation(summary = "A-06 내 정보")
	@GetMapping("/api/me")
	public MeResponse me(@CurrentUser AuthUser user) {
		return meService.me(user.userId());
	}

	@Operation(summary = "A-07 닉네임·학과 수정 (바꿀 값만 보낸다)")
	@PatchMapping("/api/me")
	public MeResponse update(@CurrentUser AuthUser user, @Valid @RequestBody UpdateMeRequest request) {
		return meService.update(user.userId(), request);
	}

}
