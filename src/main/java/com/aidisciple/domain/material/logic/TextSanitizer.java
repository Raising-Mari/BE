package com.aidisciple.domain.material.logic;

/**
 * 추출·붙여넣은 글을 DB 에 넣기 전에 정리한다. PDF 에서는 NUL 같은 제어 문자나 짝이 맞지 않는 서러게이트가 섞여 나오는데,
 * 이것이 문장에 남으면 utf8mb4 컬럼에 넣다가 실패한다. 줄바꿈·탭은 문장 분리에 쓰이므로 남긴다.
 */
public final class TextSanitizer {

	private TextSanitizer() {
	}

	public static String clean(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder cleaned = new StringBuilder(text.length());
		text.codePoints()
			.filter(TextSanitizer::keep)
			.forEach(cleaned::appendCodePoint);
		return cleaned.toString();
	}

	private static boolean keep(int codePoint) {
		if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
			return false;   // 짝이 맞지 않는 서러게이트 (짝이 맞으면 하나의 보충 문자로 합쳐져 여기 오지 않는다)
		}
		if (Character.isISOControl(codePoint)) {
			return codePoint == '\n' || codePoint == '\r' || codePoint == '\t';
		}
		return true;
	}

}
