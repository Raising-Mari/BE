package com.aidisciple.domain.teaching.service;

import java.util.List;

import com.aidisciple.domain.teaching.entity.ChatRole;
import com.aidisciple.domain.teaching.logic.ActiveNote;

/**
 * 한 턴을 처리하는 데 필요한 값. 서비스가 DB 에서 읽어 넘기며, 엔진은 DB 를 모른다.
 *
 * @param recentMessages 같은 (제자, 주차)의 최근 대화, 오래된 것부터
 * @param activeNotes    같은 (제자, 주차)의 {@code ACTIVE} 노트 전체
 */
public record TurnInput(
	Long userId,
	Long discipleId,
	String unitTitle,
	int turnNo,
	String studentMessage,
	List<ChatLine> recentMessages,
	List<ActiveNote> activeNotes
) {

	public record ChatLine(ChatRole role, String content) {
	}

}
