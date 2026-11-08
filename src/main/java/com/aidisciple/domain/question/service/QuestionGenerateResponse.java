package com.aidisciple.domain.question.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.logic.KeyElement;
import com.aidisciple.domain.question.logic.QuestionCandidate;

/** 프롬프트 question-generate 의 JSON 응답. AI 가 준 값이라 검증 전에는 믿지 않는다. */
public record QuestionGenerateResponse(List<GeneratedQuestion> questions) {

	public List<QuestionCandidate> candidates() {
		if (questions == null) {
			return List.of();
		}
		return questions.stream().filter(Objects::nonNull).map(GeneratedQuestion::toCandidate).toList();
	}

	/**
	 * gradingMode 를 문자열로 받는다. enum 으로 받으면 이상한 값 하나에 응답 전체가 파싱 실패하므로,
	 * 모르는 값은 null 로 바꿔 그 문항만 INVALID_FORMAT 으로 떨어뜨린다.
	 */
	public record GeneratedQuestion(
		String stem,
		String gradingMode,
		List<String> answers,
		List<KeyElement> keyElements,
		String referenceAnswer,
		String evidenceQuote,
		List<Integer> evidenceSentenceNos
	) {

		QuestionCandidate toCandidate() {
			return new QuestionCandidate(stem, parseMode(gradingMode), answers, keyElements, referenceAnswer,
				evidenceQuote, evidenceSentenceNos);
		}

		private static GradingMode parseMode(String value) {
			if (value == null) {
				return null;
			}
			try {
				return GradingMode.valueOf(value.strip().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return null;
			}
		}

	}

}
