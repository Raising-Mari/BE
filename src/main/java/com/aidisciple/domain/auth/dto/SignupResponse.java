package com.aidisciple.domain.auth.dto;

/** A-03 가입 완료. 바로 로그인 상태가 되도록 액세스 토큰을 함께 준다. */
public record SignupResponse(String accessToken, LoginResponse.LoginUser user) {
}
