package com.aidisciple.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.auth.config.AdminProperties;
import com.aidisciple.domain.auth.config.AuthProperties;
import com.aidisciple.domain.auth.dto.SignupRequest;
import com.aidisciple.domain.auth.dto.SignupResponse;
import com.aidisciple.domain.auth.entity.Department;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.DepartmentRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.UserRole;

class SignupServiceTest {

	private static final long SCHOOL_ID = 1L;
	private static final long DEPARTMENT_ID = 7L;
	private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
	private final JwtProvider jwtProvider = new JwtProvider(
		new AuthProperties("client", "unit-test-secret-key-0123456789-abcdefghij", 7), clock);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);

	private SignupService service = service("");

	private SignupService service(String adminEmails) {
		return new SignupService(userRepository, departmentRepository, jwtProvider, new AdminProperties(adminEmails), clock);
	}

	@BeforeEach
	void 기본_상태() {
		Department department = mock(Department.class);
		when(department.getSchoolId()).thenReturn(SCHOOL_ID);
		when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(department));
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
			User saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 42L);   // 실제로는 DB 가 정하는 값
			return saved;
		});
	}

	private String signupToken() {
		return jwtProvider.issueSignupToken("google-sub-1", "Abc@MJU.ac.kr", SCHOOL_ID);
	}

	private SignupRequest request(String nickname) {
		return new SignupRequest(signupToken(), nickname, DEPARTMENT_ID, true);
	}

	private static void assertCode(Runnable action, ErrorCode expected) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}

	// --- 가입 ---

	@Test
	void 정상_가입하면_학생으로_저장하고_바로_로그인된다() {
		SignupResponse response = service.signup(request("제자왕"));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(saved.capture());
		User user = saved.getValue();
		assertThat(user.getGoogleSub()).isEqualTo("google-sub-1");
		assertThat(user.getEmail()).isEqualTo("abc@mju.ac.kr");   // 소문자로 정규화
		assertThat(user.getNickname()).isEqualTo("제자왕");
		assertThat(user.getSchoolId()).isEqualTo(SCHOOL_ID);
		assertThat(user.getDepartmentId()).isEqualTo(DEPARTMENT_ID);
		assertThat(user.getRole()).isEqualTo(UserRole.STUDENT);
		assertThat(user.isActive()).isTrue();
		assertThat(user.getTermsAgreedAt()).isEqualTo(NOW);

		assertThat(response.user().id()).isEqualTo(42L);
		assertThat(jwtProvider.parseAccessToken(response.accessToken())).contains(new AuthUser(42L, UserRole.STUDENT));
	}

	@Test
	void 닉네임_앞뒤_공백은_잘라서_저장한다() {
		service.signup(request("  제자왕  "));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getNickname()).isEqualTo("제자왕");
	}

	@Test
	void 관리자_메일로_가입하면_ADMIN이_된다() {
		SignupResponse response = service("abc@mju.ac.kr").signup(request("제자왕"));

		assertThat(response.user().role()).isEqualTo(UserRole.ADMIN);
		assertThat(jwtProvider.parseAccessToken(response.accessToken())).contains(new AuthUser(42L, UserRole.ADMIN));
	}

	@Test
	void 가입_요청의_닉네임_외_값은_토큰의_Google_정보를_쓴다() {
		service.signup(new SignupRequest(signupToken(), "제자왕", DEPARTMENT_ID, true));

		verify(userRepository).existsByGoogleSub("google-sub-1");   // 요청 본문이 아니라 토큰에서 온 값
	}

	@Test
	void 가입_토큰이_틀렸거나_만료됐거나_액세스_토큰이면_거부한다() {
		assertCode(() -> service.signup(new SignupRequest("garbage", "제자왕", DEPARTMENT_ID, true)),
			ErrorCode.AUTH_SIGNUP_TOKEN_INVALID);
		assertCode(() -> service.signup(new SignupRequest(jwtProvider.issueAccessToken(1L, UserRole.ADMIN), "제자왕", DEPARTMENT_ID, true)),
			ErrorCode.AUTH_SIGNUP_TOKEN_INVALID);

		Clock later = Clock.fixed(NOW.plus(Duration.ofMinutes(11)), ZoneOffset.UTC);
		JwtProvider laterProvider = new JwtProvider(
			new AuthProperties("client", "unit-test-secret-key-0123456789-abcdefghij", 7), later);
		SignupService laterService = new SignupService(userRepository, departmentRepository, laterProvider,
			new AdminProperties(""), later);
		assertCode(() -> laterService.signup(request("제자왕")), ErrorCode.AUTH_SIGNUP_TOKEN_INVALID);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void 이미_가입한_계정의_가입_토큰은_다시_쓸_수_없다() {
		when(userRepository.existsByGoogleSub("google-sub-1")).thenReturn(true);

		assertCode(() -> service.signup(request("제자왕")), ErrorCode.AUTH_SIGNUP_TOKEN_INVALID);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void 약관에_동의하지_않으면_가입할_수_없다() {
		assertCode(() -> service.signup(new SignupRequest(signupToken(), "제자왕", DEPARTMENT_ID, false)),
			ErrorCode.AUTH_TERMS_REQUIRED);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void 닉네임_형식이_틀리면_가입할_수_없다() {
		for (String bad : new String[] {"a", "닉 네임", "열세글자닉네임입니다123", "관리자님", "nick!"}) {
			assertCode(() -> service.signup(request(bad)), ErrorCode.AUTH_NICKNAME_INVALID);
		}
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void 닉네임이_이미_있으면_가입할_수_없다() {
		when(userRepository.existsByNickname("제자왕")).thenReturn(true);

		assertCode(() -> service.signup(request("제자왕")), ErrorCode.AUTH_NICKNAME_TAKEN);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void 없는_학과나_다른_학교의_학과는_고를_수_없다() {
		Department otherSchool = mock(Department.class);
		when(otherSchool.getSchoolId()).thenReturn(99L);
		when(departmentRepository.findById(8L)).thenReturn(Optional.of(otherSchool));
		when(departmentRepository.findById(9L)).thenReturn(Optional.empty());

		assertCode(() -> service.signup(new SignupRequest(signupToken(), "제자왕", 8L, true)), ErrorCode.INVALID_INPUT);
		assertCode(() -> service.signup(new SignupRequest(signupToken(), "제자왕", 9L, true)), ErrorCode.INVALID_INPUT);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void 동시_가입으로_DB_UNIQUE에_걸리면_원인에_맞는_오류를_준다() {
		when(userRepository.saveAndFlush(any(User.class)))
			.thenThrow(new DataIntegrityViolationException("dup", new RuntimeException("Duplicate entry for key 'users.uk_users_nickname'")))
			.thenThrow(new DataIntegrityViolationException("dup", new RuntimeException("Duplicate entry for key 'users.uk_users_google_sub'")))
			.thenThrow(new DataIntegrityViolationException("dup", new RuntimeException("Duplicate entry for key 'users.uk_users_email'")));

		assertCode(() -> service.signup(request("제자왕")), ErrorCode.AUTH_NICKNAME_TAKEN);
		assertCode(() -> service.signup(request("제자왕")), ErrorCode.AUTH_SIGNUP_TOKEN_INVALID);
		assertCode(() -> service.signup(request("제자왕")), ErrorCode.CONFLICT);
	}

	// --- 닉네임 확인 ---

	@Test
	void 형식이_맞고_비어_있는_닉네임만_쓸_수_있다고_답한다() {
		when(userRepository.existsByNickname("제자왕")).thenReturn(false);
		when(userRepository.existsByNickname("이미있음")).thenReturn(true);

		assertThat(service.nicknameAvailability("제자왕").available()).isTrue();
		assertThat(service.nicknameAvailability(" 제자왕 ").available()).isTrue();
		assertThat(service.nicknameAvailability("이미있음").available()).isFalse();
	}

	@Test
	void 형식이_틀린_닉네임은_오류가_아니라_쓸_수_없음으로_답하고_DB는_부르지_않는다() {
		assertThat(service.nicknameAvailability("a").available()).isFalse();
		assertThat(service.nicknameAvailability("관리자").available()).isFalse();
		assertThat(service.nicknameAvailability("   ").available()).isFalse();

		verify(userRepository, never()).existsByNickname(any());
	}

}
