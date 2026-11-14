package com.aidisciple.domain.auth.dto;

/** A-05 가입 화면의 학과 선택지. 단과대 이름으로 묶어 보여 줄 수 있게 함께 준다. */
public record DepartmentResponse(Long id, String name, String collegeName) {
}
