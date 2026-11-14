package com.aidisciple.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.auth.dto.MeResponse;
import com.aidisciple.domain.auth.dto.UpdateMeRequest;
import com.aidisciple.domain.auth.entity.Department;
import com.aidisciple.domain.auth.entity.School;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.entity.UserStatus;
import com.aidisciple.domain.auth.repository.DepartmentRepository;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.security.UserRole;

class MeServiceTest {

	private static final long USER_ID = 42L;

	private final UserRepository userRepository = mock(UserRepository.class);
	private final SchoolRepository schoolRepository = mock(SchoolRepository.class);
	private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
	private final MeService service = new MeService(userRepository, schoolRepository, departmentRepository);

	private User user;

	@BeforeEach
	void 기본_상태() {
		user = userWithId(USER_ID, "제자왕", 7L);
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

		School school = BeanUtils.instantiateClass(School.class);
		ReflectionTestUtils.setField(school, "id", 1L);
		ReflectionTestUtils.setField(school, "name", "명지대학교");
		when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));

		when(departmentRepository.findById(7L)).thenReturn(Optional.of(department(7L, 1L, "컴퓨터공학전공", "반도체·ICT대학")));
	}

	private static User userWithId(long id, String nickname, Long departmentId) {
		User created = User.create(1L, departmentId, "sub-" + id, "Abc@mju.ac.kr", nickname, UserRole.STUDENT,
			Instant.parse("2026-11-06T00:00:00Z"));
		ReflectionTestUtils.setField(created, "id", id);
		return created;
	}

	private static Department department(long id, long schoolId, String name, String college) {
		Department department = BeanUtils.instantiateClass(Department.class);
		ReflectionTestUtils.setField(department, "id", id);
		ReflectionTestUtils.setField(department, "schoolId", schoolId);
		ReflectionTestUtils.setField(department, "name", name);
		ReflectionTestUtils.setField(department, "collegeName", college);
		return department;
	}

	private static void assertCode(Runnable action, ErrorCode expected) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}

	// --- A-06 ---

	@Test
	void 내_정보는_이메일을_가려서_학교와_학과를_함께_준다() {
		MeResponse me = service.me(USER_ID);

		assertThat(me.id()).isEqualTo(USER_ID);
		assertThat(me.nickname()).isEqualTo("제자왕");
		assertThat(me.emailMasked()).isEqualTo("a***@mju.ac.kr");
		assertThat(me.school()).isEqualTo(new MeResponse.School(1L, "명지대학교"));
		assertThat(me.department()).isEqualTo(new MeResponse.Department(7L, "컴퓨터공학전공", "반도체·ICT대학"));
		assertThat(me.role()).isEqualTo(UserRole.STUDENT);
	}

	@Test
	void 학과가_없는_사용자는_department가_null이다() {
		User noDepartment = userWithId(43L, "무소속", null);
		when(userRepository.findById(43L)).thenReturn(Optional.of(noDepartment));

		assertThat(service.me(43L).department()).isNull();
	}

	@Test
	void 사용자가_없거나_탈퇴했으면_로그인이_풀린_것으로_본다() {
		when(userRepository.findById(404L)).thenReturn(Optional.empty());
		User withdrawn = userWithId(44L, "탈퇴함", 7L);
		ReflectionTestUtils.setField(withdrawn, "status", UserStatus.WITHDRAWN);
		when(userRepository.findById(44L)).thenReturn(Optional.of(withdrawn));

		assertCode(() -> service.me(404L), ErrorCode.UNAUTHORIZED);
		assertCode(() -> service.me(44L), ErrorCode.UNAUTHORIZED);
	}

	// --- A-07 ---

	@Test
	void 닉네임만_바꿀_수_있다() {
		MeResponse me = service.update(USER_ID, new UpdateMeRequest("새닉네임", null));

		assertThat(me.nickname()).isEqualTo("새닉네임");
		assertThat(user.getDepartmentId()).isEqualTo(7L);
		verify(userRepository).saveAndFlush(user);
	}

	@Test
	void 학과만_바꿀_수_있다() {
		when(departmentRepository.findById(8L)).thenReturn(Optional.of(department(8L, 1L, "전자공학전공", "반도체·ICT대학")));

		MeResponse me = service.update(USER_ID, new UpdateMeRequest(null, 8L));

		assertThat(me.nickname()).isEqualTo("제자왕");
		assertThat(user.getDepartmentId()).isEqualTo(8L);
	}

	@Test
	void 내_닉네임_그대로_보내도_중복으로_보지_않는다() {
		when(userRepository.findByNickname("제자왕")).thenReturn(Optional.of(user));

		assertThat(service.update(USER_ID, new UpdateMeRequest("제자왕", null)).nickname()).isEqualTo("제자왕");
	}

	@Test
	void 다른_사람이_쓰는_닉네임으로는_바꿀_수_없다() {
		when(userRepository.findByNickname("남의닉")).thenReturn(Optional.of(userWithId(99L, "남의닉", 7L)));

		assertCode(() -> service.update(USER_ID, new UpdateMeRequest("남의닉", null)), ErrorCode.AUTH_NICKNAME_TAKEN);
		assertThat(user.getNickname()).isEqualTo("제자왕");
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void 형식이_틀린_닉네임으로는_바꿀_수_없다() {
		assertCode(() -> service.update(USER_ID, new UpdateMeRequest("a", null)), ErrorCode.AUTH_NICKNAME_INVALID);
		assertCode(() -> service.update(USER_ID, new UpdateMeRequest("관리자", null)), ErrorCode.AUTH_NICKNAME_INVALID);
	}

	@Test
	void 바꿀_값이_하나도_없으면_거부한다() {
		assertCode(() -> service.update(USER_ID, new UpdateMeRequest(null, null)), ErrorCode.INVALID_INPUT);
	}

	@Test
	void 다른_학교_학과나_없는_학과로는_바꿀_수_없다() {
		when(departmentRepository.findById(8L)).thenReturn(Optional.of(department(8L, 99L, "타교학과", "타교대학")));
		when(departmentRepository.findById(9L)).thenReturn(Optional.empty());

		assertCode(() -> service.update(USER_ID, new UpdateMeRequest(null, 8L)), ErrorCode.INVALID_INPUT);
		assertCode(() -> service.update(USER_ID, new UpdateMeRequest(null, 9L)), ErrorCode.INVALID_INPUT);
		assertThat(user.getDepartmentId()).isEqualTo(7L);
	}

}
