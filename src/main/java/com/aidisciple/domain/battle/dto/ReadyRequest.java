package com.aidisciple.domain.battle.dto;

/** B-04 본문(선택). 내 노트를 상대에게 공개할지. 생략하면 공개하지 않는다. */
public record ReadyRequest(Boolean notesPublic) {
}
