package com.aidisciple.global.error;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 에러 응답의 공통 형식. 성공 응답은 감싸지 않고 DTO 를 그대로 반환한다.
 * {@code details} 는 FE 가 오류 뒤에 이어서 할 일에 쓰는 값이다 (예: COURSE_DUPLICATE 의 기존 과목 id).
 * 값이 없으면 필드 자체를 내보내지 않아서 기존 응답 모양은 그대로다.
 */
public record ErrorResponse(
	String code,
	String message,
	List<FieldViolation> violations,
	@JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> details
) {

	public record FieldViolation(String field, String reason) {
	}

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of(), Map.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(errorCode.name(), message, List.of(), Map.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, String message, Map<String, Object> details) {
		return new ErrorResponse(errorCode.name(), message, List.of(), details);
	}

	public static ErrorResponse of(ErrorCode errorCode, List<FieldViolation> violations) {
		return new ErrorResponse(errorCode.name(), errorCode.getMessage(), violations, Map.of());
	}

}
