package com.aidisciple.domain.battle.dto;

import java.util.List;

/**
 * 노트 교환 리뷰(R-06~R-08)가 읽는 배틀 정보. {@code opponentNoteIds} 는 상대가 준비 완료를 누른 순간 스냅샷한 노트 id 이다
 * (그 뒤에 고친 노트는 보이지 않는다). {@code bothPublic} 은 두 사람 모두 노트 공개에 동의했는가.
 */
public record NoteExchangeRef(long battleId, long opponentUserId, boolean bothPublic, List<Long> opponentNoteIds) {
}
