package com.aidisciple.domain.question.service;

import java.util.List;

/** 프롬프트 battle-solve 의 JSON 응답. 노트 번호·문항 번호는 서버가 다시 확인한다. */
public record SolveLlmResponse(List<LlmAnswer> answers) {

	List<LlmAnswer> answersOrEmpty() {
		return answers == null ? List.of() : answers;
	}

	public record LlmAnswer(Long questionId, String answer, Boolean unknown, List<Long> usedNoteIds) {
	}

}
