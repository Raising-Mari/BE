package com.aidisciple.domain.course.dto;

/** 다른 도메인이 주차의 과목과 이름만 알면 될 때 쓰는 값 */
public record UnitRef(long id, long courseId, String title) {
}
