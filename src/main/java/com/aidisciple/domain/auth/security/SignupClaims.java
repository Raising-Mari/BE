package com.aidisciple.domain.auth.security;

/** 가입 토큰에 담긴 값. Google 이 확인해 준 계정 정보라서 가입 요청에서 다시 받지 않고 이 값만 믿는다. */
public record SignupClaims(String googleSub, String email, long schoolId) {
}
