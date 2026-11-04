package com.aidisciple.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.auth.dto.DevLoginRequest;
import com.aidisciple.domain.auth.dto.LoginResponse;
import com.aidisciple.domain.auth.service.DevAuthService;

/** A-09. 운영·시연 프로필에는 이 컨트롤러가 등록되지 않아 경로 자체가 404 이다. */
@RestController
@Profile("local")
@RequestMapping("/api/auth/dev-login")
@RequiredArgsConstructor
public class DevLoginController {

	private final DevAuthService devAuthService;

	@Operation(summary = "개발용 로그인 (local 프로필 전용)")
	@PostMapping
	public LoginResponse login(@Valid @RequestBody DevLoginRequest request) {
		return devAuthService.login(request.email());
	}

}
