package com.aidisciple.domain.question.logic;

import java.util.Arrays;
import java.util.List;

import com.aidisciple.global.text.TextNormalizer;

/**
 * 코드로 하는 채점 (api-spec 11-4 의 3단계). 문제 은행의 누출 시험(11-2 3단계)과 배틀 채점(WP9)이 함께 쓴다.
 * 비교는 모두 TextNormalizer 로 정규화한 뒤에 한다.
 */
public final class CodeGrader {

	static final double CORRECT_RATIO = 0.8;
	static final double WRONG_RATIO = 0.2;
	/** "공통 핵심어"로 볼 최소 길이. 한 글자 조사·기호가 겹치는 것은 핵심어로 치지 않는다. */
	private static final int MIN_KEYWORD_LENGTH = 2;

	private CodeGrader() {
	}

	/** 정답 목록형: 정답 표현 하나를 포함하면 CORRECT, 어떤 표현과도 공통 핵심어가 없으면 WRONG, 그 외 AMBIGUOUS */
	public static CodeVerdict gradeAnswerList(String answer, List<String> expectedAnswers) {
		String compactAnswer = TextNormalizer.compact(answer);
		if (compactAnswer.isEmpty()) {
			return CodeVerdict.WRONG;
		}
		boolean containsAnswer = expectedAnswers.stream()
			.map(TextNormalizer::compact)
			.anyMatch(expected -> !expected.isEmpty() && compactAnswer.contains(expected));
		if (containsAnswer) {
			return CodeVerdict.CORRECT;
		}
		boolean sharesKeyword = expectedAnswers.stream()
			.flatMap(expected -> Arrays.stream(TextNormalizer.normalize(expected).split(" ")))
			.filter(word -> word.length() >= MIN_KEYWORD_LENGTH)
			.anyMatch(compactAnswer::contains);
		return sharesKeyword ? CodeVerdict.AMBIGUOUS : CodeVerdict.WRONG;
	}

	/**
	 * 핵심 요소 중 답에 있는 비율.
	 * {@code notesText} 를 주면 그 노트에도 있는 요소만 센다 (배틀 채점 — 노트에 없는데 맞힌 것은 AI 지식 누출로 본다).
	 * null 이면 노트 조건 없이 센다 (누출 시험 — 노트가 빈 제자가 아는지를 보려는 것이라서).
	 */
	public static double keyElementRatio(String answer, List<KeyElement> elements, String notesText) {
		if (elements.isEmpty()) {
			return 0.0;
		}
		String compactAnswer = TextNormalizer.compact(answer);
		String compactNotes = notesText == null ? null : TextNormalizer.compact(notesText);
		long found = elements.stream()
			.filter(element -> element.foundIn(compactAnswer))
			.filter(element -> compactNotes == null || element.foundIn(compactNotes))
			.count();
		return (double) found / elements.size();
	}

	public static CodeVerdict gradeKeyElements(String answer, List<KeyElement> elements, String notesText) {
		double ratio = keyElementRatio(answer, elements, notesText);
		if (ratio >= CORRECT_RATIO) {
			return CodeVerdict.CORRECT;
		}
		if (ratio <= WRONG_RATIO) {
			return CodeVerdict.WRONG;
		}
		return CodeVerdict.AMBIGUOUS;
	}

}
