package com.aidisciple.domain.teaching.logic;

/**
 * 노트 변경이 탈락한 이유. 이름이 그대로 {@code rejected_note_changes.failed_checks_json} 에 저장되므로 바꾸지 않는다. (erd 4절)
 * HTTP 오류가 아니라 기록용이며 학생에게는 탈락 개수만 내려간다.
 */
public enum NoteCheckFailure {
	EVIDENCE_NOT_IN_MESSAGE,
	LOW_OVERLAP,
	NEGATION_MISMATCH,
	LENGTH_INVALID,
	NOTE_LIMIT_REACHED,
	TARGET_NOT_FOUND
}
