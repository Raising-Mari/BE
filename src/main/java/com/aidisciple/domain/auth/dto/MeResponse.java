package com.aidisciple.domain.auth.dto;

import com.aidisciple.global.security.UserRole;

/** A-06 내 정보. 이메일은 마스킹한 값만 준다. 학과가 없는 사용자는 {@code department} 가 null 이다. */
public record MeResponse(
	Long id,
	String nickname,
	String emailMasked,
	School school,
	Department department,
	UserRole role
) {

	public record School(Long id, String name) {
	}

	public record Department(Long id, String name, String collegeName) {
	}

}
