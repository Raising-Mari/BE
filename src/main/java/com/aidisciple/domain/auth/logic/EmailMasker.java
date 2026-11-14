package com.aidisciple.domain.auth.logic;

/** 응답에는 이메일 전체를 내려주지 않는다. {@code abc@mju.ac.kr} → {@code a***@mju.ac.kr} */
public final class EmailMasker {

	private static final String MASK = "***";

	private EmailMasker() {
	}

	public static String mask(String email) {
		if (email == null) {
			return MASK;
		}
		int at = email.lastIndexOf('@');
		if (at < 1) {
			return MASK;
		}
		return email.substring(0, 1) + MASK + email.substring(at);
	}

}
