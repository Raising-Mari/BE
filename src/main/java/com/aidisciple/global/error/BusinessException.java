package com.aidisciple.global.error;

import java.util.Map;

import lombok.Getter;

/** 예상 가능한 업무 오류. 서비스 계층에서 던지면 {@link GlobalExceptionHandler} 가 {@link ErrorResponse} 로 바꾼다. */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;
	private final Map<String, Object> details;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.details = Map.of();
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
		this.details = Map.of();
	}

	/** FE 가 이어서 쓸 값을 응답의 {@code details} 로 함께 보낸다. (예: 중복된 과목의 기존 id) */
	public BusinessException(ErrorCode errorCode, Map<String, Object> details) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.details = Map.copyOf(details);
	}

}
