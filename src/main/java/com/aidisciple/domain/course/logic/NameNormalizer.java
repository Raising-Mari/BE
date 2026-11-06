package com.aidisciple.domain.course.logic;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 과목명·주차 이름의 중복 판정용 정규화. "3주차"와 "3 주차", "Data Structure"와 "data structure"를 같은 이름으로 본다.
 * 공백과 보이지 않는 문자만 없애고 문장부호는 남긴다. "C++"와 "C"는 다른 과목이기 때문이다.
 * (가르치기 비교용 TextNormalizer 는 문장부호까지 지우므로 목적이 달라 따로 둔다)
 */
public final class NameNormalizer {

	private static final Pattern SPACES_AND_INVISIBLES = Pattern.compile("[\\p{Cf}\\p{Z}\\s]+");

	private NameNormalizer() {
	}

	/** 정규화한 결과가 비어 있으면 이름으로 쓸 수 없는 입력이다 (공백·보이지 않는 문자뿐). */
	public static String normalize(String input) {
		if (input == null) {
			return "";
		}
		String s = Normalizer.normalize(input, Normalizer.Form.NFKC);
		return SPACES_AND_INVISIBLES.matcher(s).replaceAll("").toLowerCase(Locale.ROOT);
	}

}
