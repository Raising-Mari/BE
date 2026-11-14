package com.aidisciple.domain.teaching.logic;

/** 같은 제자·같은 주차의 {@code ACTIVE} 노트. 검증기는 DB 를 모르므로 서비스가 읽어서 넘긴다. */
public record ActiveNote(long id, String content) {
}
