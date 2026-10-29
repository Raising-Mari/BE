package com.aidisciple.global.error;

import lombok.Getter;

/** 예상 가능한 업무 오류. 서비스 계층에서 던지면 {@link GlobalExceptionHandler} 가 {@link ErrorResponse} 로 바꾼다. */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

}
