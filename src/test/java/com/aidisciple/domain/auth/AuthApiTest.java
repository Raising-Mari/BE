package com.aidisciple.domain.auth;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.auth.service.GoogleIdentity;
import com.aidisciple.domain.auth.service.GoogleIdentityVerifier;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.security.UserRole;

/**
 * 인증 흐름을 DB(V1 시드 포함)까지 이어서 확인한다. Google 서버만 가짜로 바꾸고 나머지는 실제 코드다.
 * 각 테스트는 트랜잭션으로 감싸 끝나면 되돌리므로 테스트 DB 에 사용자가 남지 않는다.
 * IP 별 사용량 제한은 다른 테스트와 카운터를 공유하지 않도록 이 클래스에서만 크게 풀어 둔다.
 */
@SpringBootTest(properties = "app.rate-limit.google-login.limit=1000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiTest {

	private static final String SCHOOL_DOMAIN = "mju.ac.kr";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	GoogleIdentityVerifier googleIdentityVerifier;

	private static String unique() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

	private void googleReturns(String idToken, String sub, String email, String hostedDomain) {
		when(googleIdentityVerifier.verify(idToken)).thenReturn(new GoogleIdentity(sub, email, true, hostedDomain));
	}

	private ResultActions googleLogin(String idToken) throws Exception {
		return mockMvc.perform(post("/api/auth/google")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"idToken\": \"" + idToken + "\"}"));
	}

	private ResultActions signup(String signupToken, String nickname, long departmentId, boolean agreedTerms) throws Exception {
		return mockMvc.perform(post("/api/auth/signup")
			.contentType(MediaType.APPLICATION_JSON)
			.content(String.format("{\"signupToken\": \"%s\", \"nickname\": \"%s\", \"departmentId\": %d, \"agreedTerms\": %s}",
				signupToken, nickname, departmentId, agreedTerms)));
	}

	private long firstDepartmentId() throws Exception {
		String body = mockMvc.perform(get("/api/departments")).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$[0].id")).longValue();
	}

	private String read(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

	// --- 공개 API ---

	@Test
	void 학과_목록은_로그인_없이_시드된_전체를_단과대와_함께_준다() throws Exception {
		mockMvc.perform(get("/api/departments"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(75)))
			.andExpect(jsonPath("$[0].name").value("중어중문학전공"))
			.andExpect(jsonPath("$[0].collegeName").value("인문대학"));
	}

	@Test
	void 닉네임_확인은_로그인_없이_부를_수_있고_형식이_틀리면_쓸_수_없다고_답한다() throws Exception {
		mockMvc.perform(get("/api/auth/nicknames/availability").param("nickname", "n" + unique()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(true));
		mockMvc.perform(get("/api/auth/nicknames/availability").param("nickname", "관리자"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(false));
	}

	// --- 로그인 → 가입 → 내 정보 ---

	@Test
	void 처음_온_사람이_가입하고_내_정보를_보고_다시_로그인하는_전체_흐름() throws Exception {
		String suffix = unique();
		String sub = "sub-" + suffix;
		String email = "user" + suffix + "@mju.ac.kr";
		String nickname = "n" + suffix;
		googleReturns("token-" + suffix, sub, email, SCHOOL_DOMAIN);

		// 1) 처음이면 가입 토큰과 가린 이메일
		ResultActions first = googleLogin("token-" + suffix)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.registered").value(false))
			.andExpect(jsonPath("$.profile.emailMasked").value("u***@mju.ac.kr"));
		String signupToken = read(first, "$.signupToken");

		// 2) 가입 → 바로 로그인 상태
		ResultActions created = signup(signupToken, nickname, firstDepartmentId(), true)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.user.nickname").value(nickname))
			.andExpect(jsonPath("$.user.role").value("STUDENT"));
		String accessToken = read(created, "$.accessToken");

		// 3) 내 정보: 이메일은 가려서, 학교·학과 포함
		mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value(nickname))
			.andExpect(jsonPath("$.emailMasked").value("u***@mju.ac.kr"))
			.andExpect(jsonPath("$.school.name").value("명지대학교"))
			.andExpect(jsonPath("$.department.name").value("중어중문학전공"))
			.andExpect(jsonPath("$.role").value("STUDENT"));

		// 4) 같은 Google 계정으로 다시 로그인하면 액세스 토큰
		googleLogin("token-" + suffix)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.registered").value(true))
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.user.nickname").value(nickname));

		// 5) 가입 토큰은 한 번만 쓸 수 있다
		signup(signupToken, "m" + suffix, firstDepartmentId(), true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_SIGNUP_TOKEN_INVALID"));
	}

	@Test
	void 닉네임이_겹치면_두_번째_가입은_409() throws Exception {
		String suffix = unique();
		String nickname = "n" + suffix;
		long departmentId = firstDepartmentId();

		googleReturns("t1-" + suffix, "sub-a-" + suffix, "a" + suffix + "@mju.ac.kr", SCHOOL_DOMAIN);
		googleReturns("t2-" + suffix, "sub-b-" + suffix, "b" + suffix + "@mju.ac.kr", SCHOOL_DOMAIN);
		String first = read(googleLogin("t1-" + suffix), "$.signupToken");
		String second = read(googleLogin("t2-" + suffix), "$.signupToken");

		signup(first, nickname, departmentId, true).andExpect(status().isCreated());
		signup(second, nickname.toUpperCase(), departmentId, true)   // 대소문자만 달라도 같은 닉네임
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AUTH_NICKNAME_TAKEN"));
	}

	@Test
	void 약관_미동의와_형식_오류는_가입_코드로_구분해서_알려_준다() throws Exception {
		String suffix = unique();
		googleReturns("token-" + suffix, "sub-" + suffix, "c" + suffix + "@mju.ac.kr", SCHOOL_DOMAIN);
		String signupToken = read(googleLogin("token-" + suffix), "$.signupToken");
		long departmentId = firstDepartmentId();

		signup(signupToken, "n" + suffix, departmentId, false)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_TERMS_REQUIRED"));
		signup(signupToken, "a", departmentId, true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_NICKNAME_INVALID"));
	}

	// --- 거부 ---

	@Test
	void 학교_도메인이_아닌_Google_계정은_403() throws Exception {
		googleReturns("personal", "sub-p", "me@gmail.com", null);
		googleReturns("other-school", "sub-o", "me@other.ac.kr", "other.ac.kr");

		googleLogin("personal")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("AUTH_EMAIL_DOMAIN_NOT_ALLOWED"));
		googleLogin("other-school")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("AUTH_EMAIL_DOMAIN_NOT_ALLOWED"));
	}

	@Test
	void 위조된_Google_토큰은_401() throws Exception {
		when(googleIdentityVerifier.verify("forged")).thenThrow(new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID));

		googleLogin("forged")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_GOOGLE_TOKEN_INVALID"));
	}

	@Test
	void 토큰은_유효해도_없는_사용자면_내_정보는_401() throws Exception {
		String ghostToken = jwtProvider.issueAccessToken(2_000_000_000L, UserRole.STUDENT);

		mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + ghostToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void 가입_토큰으로는_내_정보를_볼_수_없다() throws Exception {
		String signupToken = jwtProvider.issueSignupToken("sub-x", "x@mju.ac.kr", 1L);

		mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + signupToken))
			.andExpect(status().isUnauthorized());
	}

	// --- 내 정보 수정 ---

	@Test
	void 닉네임과_학과를_고칠_수_있다() throws Exception {
		String suffix = unique();
		googleReturns("token-" + suffix, "sub-" + suffix, "d" + suffix + "@mju.ac.kr", SCHOOL_DOMAIN);
		String signupToken = read(googleLogin("token-" + suffix), "$.signupToken");
		String accessToken = read(signup(signupToken, "n" + suffix, firstDepartmentId(), true), "$.accessToken");

		mockMvc.perform(patch("/api/me")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\": \"new" + suffix + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("new" + suffix))
			.andExpect(jsonPath("$.department.name").value("중어중문학전공"));

		mockMvc.perform(patch("/api/me")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

}
