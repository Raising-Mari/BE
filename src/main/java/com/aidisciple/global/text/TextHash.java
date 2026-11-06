package com.aidisciple.global.text;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 문장 정확 일치 조회용 해시. 입력은 {@link TextNormalizer#normalize} 로 정규화한 문자열을 넘긴다.
 * 자료 문장 저장(material), 근거 문장 확인(question), 복붙 보정(grading)이 같은 해시를 써야 서로 찾을 수 있다.
 */
public final class TextHash {

	private TextHash() {
	}

	/** SHA-256 을 소문자 16진수 64자로 */
	public static String sha256Hex(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 을 쓸 수 없습니다.", e);
		}
	}

}
