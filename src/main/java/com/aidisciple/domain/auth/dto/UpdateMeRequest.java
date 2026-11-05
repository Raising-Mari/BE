package com.aidisciple.domain.auth.dto;

/** A-07. 바꿀 값만 보낸다. 둘 다 비어 있으면 INVALID_INPUT. 닉네임 형식·중복은 가입과 같은 규칙과 같은 오류 코드다. */
public record UpdateMeRequest(String nickname, Long departmentId) {
}
