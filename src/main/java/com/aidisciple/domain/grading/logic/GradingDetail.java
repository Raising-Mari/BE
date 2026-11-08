package com.aidisciple.domain.grading.logic;

import java.util.List;

/**
 * 채점 근거. {@code battle_answers.grading_detail_json} 로 저장돼 복기 화면(R-01)이 쓴다(erd 3절).
 * 패턴 목록·요소 목록은 내부 값이라 <b>학생용 응답에는 그대로 싣지 않는다</b>(api-spec 0절).
 *
 * @param stage          어느 단계에서 정해졌나: UNKNOWN, INJECTION, CODE, LLM, LLM_FAILED
 * @param codeVerdict    코드 채점 결과(CORRECT·WRONG·AMBIGUOUS). 모름·주입으로 정해진 답은 null
 * @param reason         AI 판정의 이유(짧게 자른 글). AI 를 거치지 않았으면 null
 */
public record GradingDetail(
	String stage,
	String codeVerdict,
	List<String> matchedElements,
	List<String> missingElements,
	List<String> elementsInNotes,
	List<String> injectionPatterns,
	double copyRatioAnswer,
	double copyRatioNotes,
	String reason
) {
}
