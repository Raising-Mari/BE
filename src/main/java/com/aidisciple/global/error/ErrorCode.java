package com.aidisciple.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 모든 에러 코드는 여기 한 곳에 모은다. FE 는 {@code code} 문자열로 분기하므로
 * 한 번 정한 코드 값은 바꾸지 않는다. 도메인 코드는 {@code AUTH_}, {@code BATTLE_} 처럼 접두사를 붙여 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
	CONFLICT(HttpStatus.CONFLICT, "이미 처리되었거나 충돌하는 요청입니다."),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

	AUTH_GOOGLE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Google 로그인 정보를 확인할 수 없습니다. 다시 로그인해 주세요."),
	AUTH_EMAIL_DOMAIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "학교 Google 계정으로만 로그인할 수 있습니다.");

	private final HttpStatus status;
	private final String message;

}
