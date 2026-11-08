package com.aidisciple.domain.teaching;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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

import com.aidisciple.ai.AiException;
import com.aidisciple.ai.LlmClient;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.Disciple;
import com.aidisciple.domain.course.entity.Unit;
import com.aidisciple.domain.course.repository.CourseRepository;
import com.aidisciple.domain.course.repository.DiscipleRepository;
import com.aidisciple.domain.course.repository.UnitRepository;
import com.aidisciple.domain.teaching.entity.NoteOp;
import com.aidisciple.domain.teaching.service.TeachingLlmResponse;
import com.aidisciple.domain.teaching.service.TeachingLlmResponse.LlmNoteChange;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.security.UserRole;

/**
 * 가르치기 API(T-01~T-03)를 DB 까지 이어서 확인한다. AI 는 가짜(LlmClient 목)로 바꿔 실제로 부르지 않는다.
 * 각 테스트는 트랜잭션으로 감싸 끝나면 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeachingApiTest {

	private static final String STUDENT_SAYS = "스택은 나중에 넣은 것이 먼저 나오는 구조야.";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	UserRepository userRepository;

	@Autowired
	SchoolRepository schoolRepository;

	@Autowired
	CourseRepository courseRepository;

	@Autowired
	UnitRepository unitRepository;

	@Autowired
	DiscipleRepository discipleRepository;

	@MockitoBean
	LlmClient llmClient;

	private String suffix;
	private long schoolId;
	private User owner;
	private long courseId;
	private long unitId;
	private long discipleId;

	@BeforeEach
	void 학생_과목_주차_제자를_만든다() {
		suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		schoolId = schoolRepository.findByCode("MJU").orElseThrow().getId();
		owner = newUser("a");
		courseId = newCourse("자료구조");
		unitId = unitRepository.saveAndFlush(Unit.create(courseId, "3주차", "3주차", owner.getId())).getId();
		discipleId = discipleRepository.saveAndFlush(Disciple.create(owner.getId(), courseId, "마리", null)).getId();
	}

	@Test
	void 메시지를_보내면_제자_답과_통과한_노트가_저장되고_근거_없는_변경은_탈락한다() throws Exception {
		aiAnswers(new TeachingLlmResponse("그럼 큐는 어떻게 달라요?", List.of(
			new LlmNoteChange(NoteOp.ADD, null, STUDENT_SAYS, STUDENT_SAYS),
			new LlmNoteChange(NoteOp.ADD, null, "큐는 먼저 넣은 것이 먼저 나온다.", "큐는 먼저 넣은 것"))));

		send(owner, unitId, STUDENT_SAYS, newClientId())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.turnNo").value(1))
			.andExpect(jsonPath("$.studentMessage.content").value(STUDENT_SAYS))
			.andExpect(jsonPath("$.discipleMessage.content").value("그럼 큐는 어떻게 달라요?"))
			.andExpect(jsonPath("$.discipleMessage.askedWhy").value(false))
			.andExpect(jsonPath("$.noteChanges.applied.length()").value(1))
			.andExpect(jsonPath("$.noteChanges.applied[0].op").value("ADD"))
			.andExpect(jsonPath("$.noteChanges.applied[0].content").value(STUDENT_SAYS))
			.andExpect(jsonPath("$.noteChanges.rejectedCount").value(1));

		mockMvc.perform(get("/api/disciples/{id}/notes", discipleId).header("Authorization", bearer(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.groups.length()").value(1))
			.andExpect(jsonPath("$.groups[0].unit.id").value(unitId))
			.andExpect(jsonPath("$.groups[0].count").value(1))
			.andExpect(jsonPath("$.groups[0].limit").value(60))
			.andExpect(jsonPath("$.groups[0].items[0].content").value(STUDENT_SAYS));

		mockMvc.perform(get("/api/disciples/{id}/messages", discipleId).header("Authorization", bearer(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.items[0].role").value("DISCIPLE"))   // 최신 먼저
			.andExpect(jsonPath("$.items[1].role").value("STUDENT"));
	}

	@Test
	void 같은_clientMessageId로_다시_보내면_AI를_다시_부르지_않고_같은_결과를_준다() throws Exception {
		aiAnswers(new TeachingLlmResponse("알겠어요!", List.of(
			new LlmNoteChange(NoteOp.ADD, null, STUDENT_SAYS, STUDENT_SAYS))));
		String clientId = newClientId();

		long firstId = idAt(send(owner, unitId, STUDENT_SAYS, clientId).andExpect(status().isCreated()),
			"$.studentMessage.id");

		send(owner, unitId, STUDENT_SAYS, clientId)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.turnNo").value(1))
			.andExpect(jsonPath("$.studentMessage.id").value(firstId))
			.andExpect(jsonPath("$.noteChanges.applied.length()").value(1));

		verify(llmClient, times(1)).generateJson(any(), eq(TeachingLlmResponse.class));
	}

	@Test
	void 세번째_턴에는_제자가_되묻는다() throws Exception {
		aiAnswers(new TeachingLlmResponse("네!", List.of()));

		send(owner, unitId, "첫 번째 설명이야.", newClientId())
			.andExpect(jsonPath("$.discipleMessage.askedWhy").value(false));
		send(owner, unitId, "두 번째 설명이야.", newClientId())
			.andExpect(jsonPath("$.discipleMessage.askedWhy").value(false));
		send(owner, unitId, "세 번째 설명이야.", newClientId())
			.andExpect(jsonPath("$.turnNo").value(3))
			.andExpect(jsonPath("$.discipleMessage.askedWhy").value(true));
	}

	@Test
	void AI가_실패하면_아무것도_저장하지_않아_재시도할_수_있다() throws Exception {
		when(llmClient.generateJson(any(), eq(TeachingLlmResponse.class)))
			.thenThrow(new AiException(ErrorCode.AI_UNAVAILABLE));

		send(owner, unitId, STUDENT_SAYS, newClientId())
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));

		mockMvc.perform(get("/api/disciples/{id}/messages", discipleId).header("Authorization", bearer(owner)))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void 남의_제자에게는_보낼_수_없다() throws Exception {
		User stranger = newUser("b");

		send(stranger, unitId, STUDENT_SAYS, newClientId())
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/disciples/{id}/notes", discipleId).header("Authorization", bearer(stranger)))
			.andExpect(status().isNotFound());

		verify(llmClient, never()).generateJson(any(), any());
	}

	@Test
	void 다른_과목의_주차로는_보낼_수_없다() throws Exception {
		long otherCourseId = newCourse("운영체제");
		long otherUnitId = unitRepository.saveAndFlush(Unit.create(otherCourseId, "1주차", "1주차", owner.getId()))
			.getId();

		send(owner, otherUnitId, STUDENT_SAYS, newClientId())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNIT_NOT_IN_COURSE"));
	}

	// --- 준비 ---

	private void aiAnswers(TeachingLlmResponse response) {
		when(llmClient.generateJson(any(), eq(TeachingLlmResponse.class))).thenReturn(response);
	}

	private ResultActions send(User user, long unit, String content, String clientMessageId) throws Exception {
		return mockMvc.perform(post("/api/disciples/{id}/messages", discipleId)
			.header("Authorization", bearer(user))
			.contentType(MediaType.APPLICATION_JSON)
			.content(String.format("{\"unitId\": %d, \"content\": \"%s\", \"clientMessageId\": \"%s\"}",
				unit, content, clientMessageId)));
	}

	private User newUser(String tag) {
		return userRepository.saveAndFlush(User.create(schoolId, null, "sub-" + tag + suffix,
			tag + suffix + "@mju.ac.kr", tag + suffix, UserRole.STUDENT, Instant.now()));
	}

	private long newCourse(String name) {
		String uniqueName = name + suffix;
		return courseRepository.saveAndFlush(
			Course.create(schoolId, null, uniqueName, uniqueName, "2026-2", owner.getId())).getId();
	}

	private String bearer(User user) {
		return "Bearer " + jwtProvider.issueAccessToken(user.getId(), user.getRole());
	}

	private static String newClientId() {
		return UUID.randomUUID().toString();
	}

	private static long idAt(ResultActions result, String path) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
	}

}
