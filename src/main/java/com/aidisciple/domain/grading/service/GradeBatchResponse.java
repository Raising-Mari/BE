package com.aidisciple.domain.grading.service;

import java.util.List;

/** 프롬프트 grade-batch 의 JSON 응답. 번호·판정 값은 서버가 다시 확인한다 — AI 결과를 그대로 믿지 않는다. */
public record GradeBatchResponse(List<LlmResult> results) {

	List<LlmResult> resultsOrEmpty() {
		return results == null ? List.of() : results;
	}

	public record LlmResult(Long answerId, String verdict, String reason) {
	}

}
