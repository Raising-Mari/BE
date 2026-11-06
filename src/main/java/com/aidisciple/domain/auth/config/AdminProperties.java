package com.aidisciple.domain.auth.config;

import java.util.Arrays;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.admin.emails}: 쉼표로 구분한 Google 계정 메일. 이 메일로 가입하면 {@code ADMIN} 이 된다. (backend-plan D-8)
 * 이미 가입한 계정의 역할은 바꾸지 않는다. 쉼표 문자열로 받는 이유는 환경변수가 비어 있을 때 목록 바인딩이 불안정해서다.
 */
@ConfigurationProperties("app.admin")
public record AdminProperties(String emails) {

	public boolean isAdmin(String email) {
		if (emails == null || emails.isBlank() || email == null) {
			return false;
		}
		String target = email.strip().toLowerCase(Locale.ROOT);
		return Arrays.stream(emails.split(","))
			.map(candidate -> candidate.strip().toLowerCase(Locale.ROOT))
			.anyMatch(candidate -> !candidate.isEmpty() && candidate.equals(target));
	}

}
