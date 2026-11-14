package com.aidisciple.domain.teaching.logic;

import com.aidisciple.domain.teaching.entity.NoteOp;

/** AI 가 제안한 노트 변경 하나. 검증을 통과하기 전에는 아무것도 반영하지 않는다. */
public record ProposedNoteChange(NoteOp op, Long targetId, String content, String evidence) {
}
