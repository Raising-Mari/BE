package com.aidisciple.domain.review.entity;

/**
 * 오답 원인 5종. DB {@code battle_answers.cause_suggestion}·{@code review_entries.user_cause} 와 같은 값 (erd 3절).
 * NOT_TAUGHT·NOT_IN_NOTES 는 누락, NOTE_WRONG 은 오류다. 서버는 후보만 제안하고 최종 원인은 학생이 고른다.
 */
public enum ReviewCause {
	NOT_TAUGHT,
	NOT_IN_NOTES,
	NOTE_WRONG,
	NOT_APPLIED,
	OTHER
}
