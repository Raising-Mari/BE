package com.aidisciple.global.error;

import java.util.List;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

	/** {@code @RequestParam}·{@code @PathVariable} 에 건 제약 위반. Spring 7 은 이 예외로 던진다. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
		List<ErrorResponse.FieldViolation> violations = e.getParameterValidationResults().stream()
			.flatMap(result -> result.getResolvableErrors().stream()
				.map(error -> new ErrorResponse.FieldViolation(
					result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
			.toList();
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
			.body(ErrorResponse.of(ErrorCode.INVALID_INPUT, violations));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
		List<ErrorResponse.FieldViolation> violations = e.getConstraintViolations().stream()
			.map(v -> new ErrorResponse.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
			.toList();
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
			.body(ErrorResponse.of(ErrorCode.INVALID_INPUT, violations));
	}

	/** 형식이 잘못된 요청. 어느 부분이 틀렸는지 내부 메시지를 싣지 않고 공통 문구만 돌려준다. */
	@ExceptionHandler({
		HttpMessageNotReadableException.class,
		MissingServletRequestParameterException.class,
		MissingServletRequestPartException.class,
		MethodArgumentTypeMismatchException.class,
		HttpRequestMethodNotSupportedException.class,
		HttpMediaTypeNotSupportedException.class,
	})
	ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus()).body(ErrorResponse.of(ErrorCode.INVALID_INPUT));
	}

	/** 없는 경로. 이 핸들러가 없으면 아래 마지막 방어선에 걸려 500 이 된다. (예: 운영 프로필의 dev-login) */
	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
		return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus()).body(ErrorResponse.of(ErrorCode.NOT_FOUND));
	}

	/** 메서드 수준 권한 검사에서 던져진 경우. 필터 단계의 거부는 {@code SecurityErrorHandler} 가 맡는다. */
	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
		return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus()).body(ErrorResponse.of(ErrorCode.FORBIDDEN));
	}

	/** 마지막 방어선. 내부 예외 메시지는 클라이언트에 내보내지 않고 로그에만 남긴다. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
	}

}
