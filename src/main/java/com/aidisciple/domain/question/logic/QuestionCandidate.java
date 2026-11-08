package com.aidisciple.domain.question.logic;

import java.util.List;
import java.util.Objects;

import com.aidisciple.domain.question.entity.GradingMode;

/**
 * AI 가 만든 후보 문항. 검증을 통과하기 전에는 저장하지 않는다.
 * {@code evidenceSentenceNos} 는 프롬프트에서 주차 문장에 1부터 붙인 번호다 (자료별 seq 가 아니다).
 */
public record QuestionCandidate(
	String stem,
	GradingMode gradingMode,
	List<String> answers,
	List<KeyElement> keyElements,
	String referenceAnswer,
	String evidenceQuote,
	List<Integer> evidenceSentenceNos
) {

	public QuestionCandidate {
		answers = answers == null ? List.of() : answers.stream().filter(Objects::nonNull).toList();
		keyElements = keyElements == null ? List.of() : keyElements.stream().filter(Objects::nonNull).toList();
		evidenceSentenceNos = evidenceSentenceNos == null ? List.of()
			: evidenceSentenceNos.stream().filter(Objects::nonNull).toList();
	}

}
