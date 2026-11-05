package com.aidisciple.global.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 강의자료 원문을 문장으로 나눈다. 결과는 원문 그대로(정규화하지 않음)이고, material_sentences.text 에 저장한다.
 * - 줄바꿈은 항상 문장 경계다. (슬라이드는 줄 단위 — erd material_sentences)
 * - 한 줄 안에서는 . ! ? 。 뒤에 공백이나 줄 끝이 오면 나눈다. 3.14 처럼 바로 글자가 오면 나누지 않는다.
 * - "1." "가." 처럼 번호만 있는 조각은 다음 내용과 붙인다.
 * - 글자가 2자 미만인 조각(빈 줄, 기호만 있는 줄)은 버린다.
 */
public final class SentenceSplitter {

	private static final int MIN_CHARS = 2;
	private static final String TERMINATORS = ".!?。";
	private static final String CLOSERS = "\"'”’)]」』";
	private static final Pattern LIST_MARKER = Pattern.compile("\\d{1,3}|[a-zA-Z]|[가-힣]");

	private SentenceSplitter() {
	}

	public static List<String> split(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		List<String> sentences = new ArrayList<>();
		String unified = text.replace("\r\n", "\n").replace('\r', '\n');
		for (String line : unified.split("\n")) {
			splitLine(line, sentences);
		}
		return sentences;
	}

	private static void splitLine(String line, List<String> out) {
		StringBuilder current = new StringBuilder();
		int i = 0;
		while (i < line.length()) {
			char c = line.charAt(i++);
			current.append(c);
			if (TERMINATORS.indexOf(c) < 0) {
				continue;
			}
			// ?! … 처럼 이어진 끝맺음 부호와 닫는 따옴표·괄호까지 한 문장에 넣는다
			while (i < line.length() && TERMINATORS.indexOf(line.charAt(i)) >= 0) {
				current.append(line.charAt(i++));
			}
			while (i < line.length() && CLOSERS.indexOf(line.charAt(i)) >= 0) {
				current.append(line.charAt(i++));
			}
			boolean atBoundary = i >= line.length() || Character.isWhitespace(line.charAt(i));
			if (atBoundary && !isListMarker(current)) {
				add(out, current);
				current.setLength(0);
			}
		}
		add(out, current);
	}

	private static boolean isListMarker(StringBuilder piece) {
		String s = piece.toString().strip();
		return s.endsWith(".") && LIST_MARKER.matcher(s.substring(0, s.length() - 1)).matches();
	}

	private static void add(List<String> out, StringBuilder piece) {
		String sentence = piece.toString().strip();
		if (TextNormalizer.compact(sentence).length() >= MIN_CHARS) {
			out.add(sentence);
		}
	}
}
