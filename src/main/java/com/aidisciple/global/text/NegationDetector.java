package com.aidisciple.global.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 부정 표지 개수를 센다. 근거와 노트의 개수 홀짝이 다르면 "A이다"가 "A가 아니다"로 뒤집힌 것으로 본다. (api-spec 11-1)
 * 표지: 않, 안, 없, 못, 아니, 아님, 불가, X(독립된 글자만)
 * 알려진 한계: "안정", "안내" 처럼 '안'이 들어간 일반 단어도 센다. 근거와 노트가 같은 단어를 쓰면 홀짝은 그대로라 대부분 문제없다.
 */
public final class NegationDetector {

	private static final Pattern KOREAN = Pattern.compile("않|안|없|못|아니|아님|불가");
	private static final Pattern X_MARK = Pattern.compile("(?<![a-z0-9])x(?![a-z0-9])");

	private NegationDetector() {
	}

	public static int count(String text) {
		String s = TextNormalizer.normalize(text);
		return countMatches(KOREAN, s) + countMatches(X_MARK, s);
	}

	/** 두 문장의 부정 표지 개수 홀짝이 같은가 */
	public static boolean sameParity(String a, String b) {
		return count(a) % 2 == count(b) % 2;
	}

	private static int countMatches(Pattern pattern, String s) {
		Matcher matcher = pattern.matcher(s);
		int count = 0;
		while (matcher.find()) {
			count++;
		}
		return count;
	}
}
