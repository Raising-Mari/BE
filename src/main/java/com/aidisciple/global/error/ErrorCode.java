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
	NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "아직 구현되지 않은 API입니다."),

	AUTH_GOOGLE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Google 로그인 정보를 확인할 수 없습니다. 다시 로그인해 주세요."),
	AUTH_EMAIL_DOMAIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "학교 Google 계정으로만 로그인할 수 있습니다."),
	AUTH_SIGNUP_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "가입 시간이 지났거나 이미 사용한 가입 정보입니다. Google 로그인부터 다시 해 주세요."),
	AUTH_NICKNAME_TAKEN(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
	AUTH_NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "닉네임은 2~12자의 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있습니다."),
	AUTH_TERMS_REQUIRED(HttpStatus.BAD_REQUEST, "이용약관과 개인정보 처리에 동의해야 가입할 수 있습니다."),

	COURSE_DUPLICATE(HttpStatus.CONFLICT, "같은 학기에 같은 이름의 과목이 이미 있습니다."),
	UNIT_DUPLICATE(HttpStatus.CONFLICT, "같은 이름의 주차가 이미 있습니다."),
	UNIT_NOT_IN_COURSE(HttpStatus.BAD_REQUEST, "이 과목의 주차가 아닙니다."),
	DISCIPLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이 과목에는 이미 제자가 있습니다."),
	DISCIPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "이 과목에 제자가 없습니다. 먼저 제자를 만들어 주세요."),
	TEACHING_CONCURRENT_TURN(HttpStatus.CONFLICT, "같은 대화에 메시지가 동시에 들어왔습니다. 잠시 후 다시 보내 주세요."),

	MATERIAL_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "자료 사용 권한과 개인정보 제외에 동의해야 올릴 수 있습니다."),
	MATERIAL_INVALID_FILE(HttpStatus.BAD_REQUEST, "PDF 파일만 올릴 수 있습니다."),
	MATERIAL_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "자료가 너무 큽니다. 파일은 20MB, 붙여넣은 글은 10만 자까지 올릴 수 있습니다."),

	AI_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AI 사용량이 많아 잠시 쉬어야 합니다. 조금 뒤에 다시 시도해 주세요."),
	AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),
	AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 응답이 늦어지고 있습니다. 다시 시도해 주세요."),
	AI_BAD_RESPONSE(HttpStatus.BAD_GATEWAY, "AI 응답을 처리하지 못했습니다. 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String message;

}
