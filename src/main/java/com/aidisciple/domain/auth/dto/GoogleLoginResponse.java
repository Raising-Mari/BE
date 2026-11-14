package com.aidisciple.domain.auth.dto;

/**
 * A-01. 가입한 사람이면 {@code registered=true} 와 액세스 토큰, 처음이면 {@code registered=false} 와 가입 토큰(10분, 1회용)을 준다.
 * 두 경우의 필드를 한 record 에 두고 쓰지 않는 쪽은 null 이다.
 */
public record GoogleLoginResponse(
	boolean registered,
	String accessToken,
	LoginResponse.LoginUser user,
	String signupToken,
	SignupProfile profile
) {

	/** 가입 화면에 보여 줄 값. 이메일 전체는 내려주지 않는다. */
	public record SignupProfile(String emailMasked) {
	}

}
