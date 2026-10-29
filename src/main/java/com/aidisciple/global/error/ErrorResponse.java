package com.aidisciple.global.error;

import java.util.List;

/** 모든 에러 응답의 공통 형식. 성공 응답은 감싸지 않고 DTO 를 그대로 반환한다. */
public record ErrorResponse(String code, String message, List<FieldViolation> violations) {

	public record FieldViolation(String field, String reason) {
	}

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(errorCode.name(), message, List.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, List<FieldViolation> violations) {
		return new ErrorResponse(errorCode.name(), errorCode.getMessage(), violations);
	}

}
