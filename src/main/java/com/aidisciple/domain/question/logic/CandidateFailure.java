package com.aidisciple.domain.question.logic;

/**
 * 후보 문항이 탈락한 이유. 이름이 그대로 question_validation_logs.detail_json 에 남으므로 바꾸지 않는다.
 * LEAKED 는 누출 시험(3단계)에서 노트가 빈 제자가 맞힌 경우다.
 */
public enum CandidateFailure {
	INVALID_FORMAT,
	EVIDENCE_NOT_FOUND,
	ANSWER_NOT_IN_EVIDENCE,
	DUPLICATE,
	LEAKED
}
