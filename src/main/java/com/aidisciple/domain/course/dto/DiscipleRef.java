package com.aidisciple.domain.course.dto;

/** 다른 도메인(가르치기 등)이 제자의 주인과 과목만 알면 될 때 쓰는 값. 엔티티를 밖으로 내보내지 않는다. */
public record DiscipleRef(long id, long userId, long courseId) {
}
