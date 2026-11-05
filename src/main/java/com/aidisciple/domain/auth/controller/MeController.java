package com.aidisciple.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.auth.dto.MeResponse;
import com.aidisciple.global.api.NotImplemented;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

@Tag(name = "인증")
@RestController
public class MeController {

	@Operation(summary = "A-06 내 정보")
	@GetMapping("/api/me")
	public MeResponse me(@CurrentUser AuthUser user) {
		return NotImplemented.yet();
	}

}
