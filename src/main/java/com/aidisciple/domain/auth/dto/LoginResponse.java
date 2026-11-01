package com.aidisciple.domain.auth.dto;

import com.aidisciple.global.security.UserRole;

/** 로그인 응답(api-spec A-01 의 {@code registered=true} 쪽). 가입 토큰 응답은 가입 기능(WP3)에서 추가한다. */
public record LoginResponse(boolean registered, String accessToken, LoginUser user) {

	public record LoginUser(Long id, String nickname, UserRole role) {
	}

}
