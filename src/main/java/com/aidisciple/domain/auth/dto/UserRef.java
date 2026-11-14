package com.aidisciple.domain.auth.dto;

/**
 * 다른 도메인이 "이 사용자가 어느 학교·학과인가"만 알면 될 때 쓰는 값. 엔티티를 밖으로 내보내지 않기 위한 얇은 창구다.
 * {@code departmentId} 는 학과가 없는 사용자면 null.
 */
public record UserRef(long id, long schoolId, Long departmentId) {
}
