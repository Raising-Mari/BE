package com.aidisciple.domain.teaching.entity;

/** 노트 변경 종류. DB {@code rejected_note_changes.op} 와 같은 값 (erd 3절) */
public enum NoteOp {
	ADD,
	UPDATE,
	REMOVE
}
