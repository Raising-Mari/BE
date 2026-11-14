package com.aidisciple.domain.auth.dto;

import com.aidisciple.global.security.UserRole;

/** 로그인한 사용자 요약({@link LoginUser}). 로그인·가입 응답이 공통으로 담는다. 응답 본문 자체는 {@code GoogleLoginResponse}·{@code SignupResponse}. */
public record LoginResponse(boolean registered, String accessToken, LoginUser user) {

	public record LoginUser(Long id, String nickname, UserRole role) {
	}

}
