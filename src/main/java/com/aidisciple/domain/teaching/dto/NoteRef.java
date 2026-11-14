package com.aidisciple.domain.teaching.dto;

/**
 * 다른 도메인(배틀)이 노트의 id 와 내용만 알면 될 때 쓰는 값. 엔티티를 밖으로 내보내지 않는다.
 * 노트 내용은 저장한 뒤 바뀌지 않으므로(고치면 새 노트가 생긴다) 스냅샷한 id 로 읽어도 그 순간의 내용이다.
 */
public record NoteRef(long id, String content) {
}
