package com.aidisciple.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** A-01. FE 가 Google 로그인으로 받은 ID 토큰. 서버는 검증에만 쓰고 저장하지 않는다. */
public record GoogleLoginRequest(@NotBlank String idToken) {
}
