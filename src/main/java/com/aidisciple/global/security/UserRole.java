package com.aidisciple.global.security;

/** 사용자 역할. 모든 도메인의 권한 검사가 쓰므로 {@code global} 에 둔다. DB 의 {@code users.role} 값과 같다. */
public enum UserRole {
	STUDENT,
	ADMIN
}
