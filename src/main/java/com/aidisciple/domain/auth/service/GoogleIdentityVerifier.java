package com.aidisciple.domain.auth.service;

/**
 * Google ID 토큰을 확인하는 경계. 외부 네트워크를 쓰므로 DB 트랜잭션 안에서 부르지 않는다.
 * 테스트에서는 이 인터페이스에 가짜 구현을 꽂는다.
 */
public interface GoogleIdentityVerifier {

	/** 서명·발급자·대상(aud)·만료가 맞지 않으면 {@code AUTH_GOOGLE_TOKEN_INVALID}. */
	GoogleIdentity verify(String idToken);

}
