package com.aidisciple.domain.battle.logic;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;

/**
 * 방 코드 6자리. 친구에게 말로 불러 주거나 손으로 옮겨 적는 값이라 헷갈리는 글자(0·O, 1·I·L)를 뺀다.
 * 입력은 대소문자·공백을 무시하고 맞춘다.
 */
public final class RoomCodes {

	public static final int LENGTH = 6;
	static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

	private static final Random RANDOM = new SecureRandom();

	private RoomCodes() {
	}

	public static String generate() {
		return generate(RANDOM);
	}

	static String generate(Random random) {
		StringBuilder code = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

	/** 공백(전각 포함)을 모두 없애고 대문자로 맞춘다. 6자리 형식이 아니면 null — 그런 코드의 방은 있을 수 없다. */
	public static String normalize(String input) {
		if (input == null) {
			return null;
		}
		String code = input.replaceAll("[\\s\\u3000]+", "").toUpperCase(Locale.ROOT);
		if (code.length() != LENGTH) {
			return null;
		}
		for (int i = 0; i < code.length(); i++) {
			if (ALPHABET.indexOf(code.charAt(i)) < 0) {
				return null;
			}
		}
		return code;
	}

}
