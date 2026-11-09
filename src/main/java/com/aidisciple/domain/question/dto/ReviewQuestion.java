package com.aidisciple.domain.question.dto;

/**
 * 복기(R-01)가 문항을 보여 줄 때 쓰는 값. 모범 답·근거 문장이 들어 있다 —
 * 정답 보기(R-11) 전에는 학생에게 내려가면 안 되고, 그 판단은 복기 도메인이 한다.
 * {@code materialTitle} 은 근거 문장이 들어 있는 자료 제목(없으면 null),
 * {@code practiceUsable} 은 연습 배틀에서 다시 낼 수 있는가(UNDER_REVIEW·RETIRED 면 false).
 */
public record ReviewQuestion(long id, String stem, String referenceAnswer, String evidenceText, String materialTitle,
							 boolean practiceUsable) {
}
