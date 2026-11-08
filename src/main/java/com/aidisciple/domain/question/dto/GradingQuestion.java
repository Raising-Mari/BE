package com.aidisciple.domain.question.dto;

import java.util.List;

import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.logic.KeyElement;

/**
 * 채점(WP9)이 문항 하나를 채점하는 데 필요한 값. 학생에게 내려보내는 값이 아니다 — 정답·핵심 요소·모범 답·근거가 들어 있다.
 * {@code answers} 는 ANSWER_LIST 일 때, {@code keyElements} 는 KEY_ELEMENTS 일 때만 채워진다.
 */
public record GradingQuestion(long id, String stem, GradingMode gradingMode, List<String> answers,
							  List<KeyElement> keyElements, String referenceAnswer, String evidenceText) {

	public GradingQuestion {
		answers = answers == null ? List.of() : List.copyOf(answers);
		keyElements = keyElements == null ? List.of() : List.copyOf(keyElements);
	}

}
