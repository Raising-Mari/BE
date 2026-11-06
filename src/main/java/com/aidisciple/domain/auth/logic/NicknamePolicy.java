package com.aidisciple.domain.auth.logic;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 닉네임 규칙(api-spec A-03): 2~12자, 한글·영문·숫자·밑줄. 중복은 DB 를 봐야 하므로 여기서 판단하지 않는다.
 * 운영진을 사칭하는 이름은 막는다. 욕설 같은 부적절 표현 사전은 아직 없다. (시험 사용에서 필요해지면 추가)
 */
public final class NicknamePolicy {

	private static final Pattern FORMAT = Pattern.compile("^[가-힣a-zA-Z0-9_]{2,12}$");

	/** 일부만 들어 있어도 막는 말 */
	private static final List<String> RESERVED_PARTS = List.of("admin", "관리자", "운영자", "운영진");

	/** 정확히 같을 때만 막는 말 ("시스템생명" 처럼 학과 이름이 들어간 닉네임은 허용해야 한다) */
	private static final Set<String> RESERVED_EXACT = Set.of("system", "시스템");

	private NicknamePolicy() {
	}

	public static boolean isValid(String nickname) {
		if (nickname == null || !FORMAT.matcher(nickname).matches()) {
			return false;
		}
		String lower = nickname.toLowerCase(Locale.ROOT);
		return !RESERVED_EXACT.contains(lower) && RESERVED_PARTS.stream().noneMatch(lower::contains);
	}

}
