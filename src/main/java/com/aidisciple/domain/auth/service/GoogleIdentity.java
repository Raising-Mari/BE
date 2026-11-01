package com.aidisciple.domain.auth.service;

/**
 * 검증을 통과한 Google ID 토큰의 내용. {@code hostedDomain} 은 Workspace 도메인(토큰의 {@code hd})이고 개인 계정이면 null 이다.
 * 이 도메인이 학교와 같은지는 호출하는 쪽(로그인 서비스)이 판단한다.
 */
public record GoogleIdentity(String subject, String email, boolean emailVerified, String hostedDomain) {
}
