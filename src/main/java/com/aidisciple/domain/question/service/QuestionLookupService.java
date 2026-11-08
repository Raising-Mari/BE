package com.aidisciple.domain.question.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.aidisciple.domain.question.dto.GradingQuestion;
import com.aidisciple.domain.question.entity.Question;
import com.aidisciple.domain.question.logic.KeyElement;
import com.aidisciple.domain.question.repository.QuestionRepository;

/**
 * 채점(WP9)이 문항의 정답·핵심 요소·모범 답·근거를 읽는 통로. 학생에게 내려보내는 값이 아니라 서버 안에서 채점에만 쓴다.
 * 문항 JSON 은 문제 은행이 만든 값이라 읽을 수 없으면 데이터가 깨진 것이므로 예외로 알린다(오답으로 둔갑시키지 않는다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionLookupService {

	private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
	};
	private static final TypeReference<List<KeyElement>> KEY_ELEMENTS = new TypeReference<>() {
	};

	private final QuestionRepository questionRepository;
	private final JsonMapper jsonMapper;

	public Map<Long, GradingQuestion> gradingQuestions(Collection<Long> questionIds) {
		if (questionIds.isEmpty()) {
			return Map.of();
		}
		return questionRepository.findAllById(questionIds).stream()
			.collect(Collectors.toMap(Question::getId, this::toGradingQuestion));
	}

	private GradingQuestion toGradingQuestion(Question question) {
		List<String> answers = question.getAnswersJson() == null ? List.of()
			: jsonMapper.readValue(question.getAnswersJson(), STRINGS);
		List<KeyElement> elements = question.getKeyElementsJson() == null ? List.of()
			: jsonMapper.readValue(question.getKeyElementsJson(), KEY_ELEMENTS);
		return new GradingQuestion(question.getId(), question.getStem(), question.getGradingMode(), answers, elements,
			question.getReferenceAnswer(), question.getEvidenceText());
	}

}
