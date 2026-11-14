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

import com.aidisciple.domain.material.service.MaterialSourceService;
import com.aidisciple.domain.question.dto.GradingQuestion;
import com.aidisciple.domain.question.dto.ReviewQuestion;
import com.aidisciple.domain.question.entity.Question;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.logic.KeyElement;
import com.aidisciple.domain.question.repository.QuestionEvidenceRepository;
import com.aidisciple.domain.question.repository.QuestionRepository;

/**
 * 채점(WP9)·복기(WP11)가 문항의 정답·핵심 요소·모범 답·근거를 읽는 통로. 학생에게 그대로 내려보내는 값이 아니다.
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
	private final QuestionEvidenceRepository evidenceRepository;
	private final MaterialSourceService materialSourceService;
	private final JsonMapper jsonMapper;

	public Map<Long, GradingQuestion> gradingQuestions(Collection<Long> questionIds) {
		if (questionIds.isEmpty()) {
			return Map.of();
		}
		return questionRepository.findAllById(questionIds).stream()
			.collect(Collectors.toMap(Question::getId, this::toGradingQuestion));
	}

	/** 복기(R-01)용. 근거 문장이 여러 개면 가장 앞(id 가 작은) 문장의 자료 제목을 쓴다. */
	public Map<Long, ReviewQuestion> reviewQuestions(Collection<Long> questionIds) {
		if (questionIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Long> firstSentenceByQuestion = evidenceRepository.findByIdQuestionIdIn(questionIds).stream()
			.collect(Collectors.toMap(evidence -> evidence.getId().getQuestionId(),
				evidence -> evidence.getId().getSentenceId(), Math::min));
		Map<Long, String> titleBySentence = materialSourceService.materialTitlesOf(firstSentenceByQuestion.values());
		return questionRepository.findAllById(questionIds).stream()
			.collect(Collectors.toMap(Question::getId, question -> new ReviewQuestion(question.getId(), question.getStem(),
				question.getReferenceAnswer(), question.getEvidenceText(),
				titleBySentence.get(firstSentenceByQuestion.get(question.getId())),
				question.getStatus() != QuestionStatus.UNDER_REVIEW && question.getStatus() != QuestionStatus.RETIRED)));
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
