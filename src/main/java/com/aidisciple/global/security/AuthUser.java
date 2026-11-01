package com.aidisciple.global.security;

/**
 * 인증된 요청의 사용자. 액세스 토큰에 담긴 값만 가지므로 요청마다 DB 를 조회하지 않는다.
 * 최신 상태(탈퇴 여부 등)가 필요한 곳은 서비스에서 {@code userId} 로 다시 조회한다.
 */
public record AuthUser(long userId, UserRole role) {
}
