package com.aidisciple.global.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 텍스트 비교용 정규화. 가르치기 검증·문항 근거 확인·채점·복붙 보정이 함께 쓴다.
 * 원문을 바꾸지 않는다. 비교할 때만 쓰고, 저장·화면에는 원문을 쓴다.
 */
public final class TextNormalizer {

	private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}+");
	private static final Pattern PUNCT_OR_SYMBOL = Pattern.compile("[\\p{P}\\p{S}]+");
	private static final Pattern WHITESPACE =
		Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

	private TextNormalizer() {
	}

	/** 비교용 정규화. 단어 사이 공백 하나는 남긴다. */
	public static String normalize(String input) {
		if (input == null || input.isBlank()) {
			return "";
		}
		String s = Normalizer.normalize(input, Normalizer.Form.NFKC);
		s = INVISIBLE.matcher(s).replaceAll("");
		s = s.toLowerCase(Locale.ROOT);
		s = PUNCT_OR_SYMBOL.matcher(s).replaceAll(" ");
		s = WHITESPACE.matcher(s).replaceAll(" ");
		return s.trim();
	}

	/** 글자 n-gram용. 공백까지 모두 없앤다. */
	public static String compact(String input) {
		return normalize(input).replace(" ", "");
	}
}
