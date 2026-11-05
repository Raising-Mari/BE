package com.aidisciple.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A-03. 닉네임 형식(2~12자, 한글·영문·숫자·_)과 약관 동의는 서비스가 검사한다.
 * Bean Validation 으로 막으면 INVALID_INPUT 이 나가서 FE 가 분기하는 AUTH_NICKNAME_INVALID·AUTH_TERMS_REQUIRED 를 줄 수 없기 때문이다.
 */
public record SignupRequest(
	@NotBlank String signupToken,
	@NotBlank String nickname,
	@NotNull Long departmentId,
	boolean agreedTerms
) {
}
