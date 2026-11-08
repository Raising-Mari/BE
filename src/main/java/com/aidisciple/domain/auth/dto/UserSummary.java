package com.aidisciple.domain.auth.dto;

/**
 * 다른 도메인(랭킹 등)이 화면에 사용자를 보여 줄 때 필요한 값만 담은 것. 엔티티를 밖으로 내보내지 않기 위한 얇은 창구다.
 * {@code departmentId} 는 학과가 없는 사용자면 null.
 */
public record UserSummary(long id, String nickname, Long departmentId) {
}
