package com.aidisciple.global.error;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보안 필터 단계에서 거부된 요청(401·403)도 컨트롤러 오류와 같은 {@link ErrorResponse} 형식으로 답한다.
 * 필터 단계의 예외는 {@link GlobalExceptionHandler} 에 닿지 않아서 따로 필요하다.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
		write(response, ErrorCode.UNAUTHORIZED);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException {
		write(response, ErrorCode.FORBIDDEN);
	}

	private void write(HttpServletResponse response, ErrorCode code) throws IOException {
		response.setStatus(code.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		jsonMapper.writeValue(response.getWriter(), ErrorResponse.of(code));
	}

}
