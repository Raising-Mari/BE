package com.aidisciple.domain.question.entity;

/**
 * DB {@code questions.status}. 배틀에는 VERIFIED 를 쓰고, UNJUDGED(외부 판정 전)는
 * app.question.allow-unjudged 가 켜졌을 때만 쓴다. UNDER_REVIEW·RETIRED 는 배틀에서 빠진다.
 */
public enum QuestionStatus {
	VERIFIED,
	UNJUDGED,
	UNDER_REVIEW,
	RETIRED
}
