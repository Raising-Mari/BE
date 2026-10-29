package com.aidisciple.global.error;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		ErrorCode code = e.getErrorCode();
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		List<ErrorResponse.FieldViolation> violations = e.getBindingResult().getFieldErrors().stream()
			.map(fe -> new ErrorResponse.FieldViolation(fe.getField(), fe.getDefaultMessage()))
			.toList();
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
			.body(ErrorResponse.of(ErrorCode.INVALID_INPUT, violations));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus()).body(ErrorResponse.of(ErrorCode.INVALID_INPUT));
	}

	/** 마지막 방어선. 내부 예외 메시지는 클라이언트에 내보내지 않고 로그에만 남긴다. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
	}

}
