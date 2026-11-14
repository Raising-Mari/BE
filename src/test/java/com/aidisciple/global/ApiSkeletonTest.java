package com.aidisciple.global;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.global.security.UserRole;

/**
 * API 계약 스켈레톤(WP 2-C)이 Swagger 에 나오고, 구현 전에는 501 NOT_IMPLEMENTED 를 돌려주는지 확인한다.
 * 기능을 구현하면 해당 경로를 이 테스트에서 빼고 그 도메인의 테스트로 옮긴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSkeletonTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@Test
	void 스켈레톤_경로가_API_문서에_나온다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/courses/{courseId}/units']").exists())
			.andExpect(jsonPath("$.paths['/api/disciples/{discipleId}/messages']").exists())
			.andExpect(jsonPath("$.paths['/api/battles/{battleId}/ready']").exists())
			.andExpect(jsonPath("$.paths['/api/rankings']").exists());
	}

	@Test
	void 요청_값이_틀리면_구현전이어도_400을_준다() throws Exception {
		mockMvc.perform(post("/api/battles")
				.header("Authorization", "Bearer " + studentToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"courseId\": 1, \"unitId\": 3, \"questionCount\": 20}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	private String studentToken() {
		return jwtProvider.issueAccessToken(1L, UserRole.STUDENT);
	}

}
