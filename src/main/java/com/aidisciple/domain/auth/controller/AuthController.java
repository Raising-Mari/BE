package com.aidisciple.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.auth.dto.GoogleLoginRequest;
import com.aidisciple.domain.auth.dto.GoogleLoginResponse;
import com.aidisciple.domain.auth.dto.NicknameAvailabilityResponse;
import com.aidisciple.domain.auth.dto.SignupRequest;
import com.aidisciple.domain.auth.dto.SignupResponse;
import com.aidisciple.domain.auth.service.GoogleLoginService;
import com.aidisciple.domain.auth.service.SignupService;

/** 1절 인증 (WP3). 경로가 {@code /api/auth/**} 라서 모두 공개 경로다. */
@Tag(name = "인증")
@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final GoogleLoginService googleLoginService;
	private final SignupService signupService;

	@Operation(summary = "A-01 Google 로그인 (가입했으면 액세스 토큰, 처음이면 가입 토큰)")
	@PostMapping("/google")
	public GoogleLoginResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request, HttpServletRequest http) {
		return googleLoginService.login(request.idToken(), http.getRemoteAddr());
	}

	@Operation(summary = "A-03 가입 (닉네임·학과)")
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
		return signupService.signup(request);
	}

	@Operation(summary = "A-04 닉네임 중복 확인")
	@GetMapping("/nicknames/availability")
	public NicknameAvailabilityResponse nicknameAvailability(@RequestParam @NotBlank String nickname) {
		return signupService.nicknameAvailability(nickname);
	}

}
