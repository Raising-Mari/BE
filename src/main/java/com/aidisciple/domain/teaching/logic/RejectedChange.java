package com.aidisciple.domain.teaching.logic;

import java.util.List;

/** 검증에서 탈락한 변경과 이유. {@code rejected_note_changes} 에 남긴다. 중복(병합)은 탈락이 아니므로 여기에 없다. */
public record RejectedChange(ProposedNoteChange change, List<NoteCheckFailure> failedChecks) {
}
