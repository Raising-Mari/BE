package com.aidisciple.domain.course;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.entity.School;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.repository.CourseRepository;
import com.aidisciple.global.security.UserRole;

/**
 * 과목·주차·제자 API 를 DB(V1 시드 포함)까지 이어서 확인한다. 검색 쿼리와 UNIQUE 제약은 단위 테스트로는 볼 수 없는 부분이다.
 * 각 테스트는 트랜잭션으로 감싸 끝나면 되돌리므로 테스트 DB 에 데이터가 남지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CourseApiTest {

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
	JdbcTemplate jdbcTemplate;

	private String suffix;
	private long schoolId;
	private String token;
	private long userId;

	@BeforeEach
	void 내_학교의_학생_한_명을_만든다() {
		suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		schoolId = schoolRepository.findByCode("MJU").orElseThrow().getId();
		User user = newUser("a");
		userId = user.getId();
		token = bearer(user);
	}

	private User newUser(String tag) {
		User user = userRepository.saveAndFlush(User.create(schoolId, null, "sub-" + tag + suffix,
			tag + suffix + "@mju.ac.kr", tag + suffix, UserRole.STUDENT, Instant.now()));
		return user;
	}

	private String bearer(User user) {
		return "Bearer " + jwtProvider.issueAccessToken(user.getId(), user.getRole());
	}

	private ResultActions createCourse(String authorization, String name, String term) throws Exception {
		return mockMvc.perform(post("/api/courses")
			.header("Authorization", authorization)
			.contentType(MediaType.APPLICATION_JSON)
			.content(String.format("{\"name\": \"%s\", \"term\": \"%s\"}", name, term)));
	}

	private long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	// --- 과목 ---

	@Test
	void 과목을_만들고_같은_과목은_기존_id와_함께_409로_막는다() throws Exception {
		String name = "자료구조" + suffix;

		long id = idOf(createCourse(token, name, "2026-2")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value(name))
			.andExpect(jsonPath("$.term").value("2026-2"))
			.andExpect(jsonPath("$.hasMyDisciple").value(false)));

		createCourse(token, " 자료 구조" + suffix + " ", "2026-2")   // 공백만 다른 같은 이름
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COURSE_DUPLICATE"))
			.andExpect(jsonPath("$.details.existingCourseId").value(id));

		createCourse(token, name, "2026-1").andExpect(status().isCreated());   // 학기가 다르면 새 과목
	}

	@Test
	void 요청_값이_틀리면_400이다() throws Exception {
		createCourse(token, "", "2026-2")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		createCourse(token, "자료구조" + suffix, "2026")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 학과를_고르면_과목에_학과가_붙는다() throws Exception {
		long departmentId = ((Number) JsonPath.read(
			mockMvc.perform(get("/api/departments")).andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();

		mockMvc.perform(post("/api/courses")
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(String.format("{\"name\": \"학과과목%s\", \"departmentId\": %d, \"term\": \"2026-2\"}", suffix, departmentId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.department.id").value(departmentId))
			.andExpect(jsonPath("$.department.name").value("중어중문학전공"));
	}

	@Test
	void 없는_학과를_고르면_400이다() throws Exception {
		mockMvc.perform(post("/api/courses")
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"학과없음" + suffix + "\", \"departmentId\": 2000000000, \"term\": \"2026-2\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 검색은_이름_일부로_찾고_내_제자_유무를_함께_준다() throws Exception {
		long withDisciple = idOf(createCourse(token, "알고리즘" + suffix, "2026-2"));
		createCourse(token, "운영체제" + suffix, "2026-2");
		mockMvc.perform(post("/api/courses/" + withDisciple + "/disciple")
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"마리\"}"))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/courses").header("Authorization", token).param("keyword", "알고 리즘" + suffix))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].name").value("알고리즘" + suffix))
			.andExpect(jsonPath("$.items[0].hasMyDisciple").value(true));

		mockMvc.perform(get("/api/courses").header("Authorization", token).param("keyword", suffix))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void 검색어의_퍼센트와_밑줄은_와일드카드가_아니라_글자로_찾는다() throws Exception {
		createCourse(token, "검색과목" + suffix, "2026-2");

		mockMvc.perform(get("/api/courses").header("Authorization", token).param("keyword", "%"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/courses").header("Authorization", token).param("keyword", "_"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void 검색은_페이지로_나누고_학기_최신순이다() throws Exception {
		createCourse(token, "페이지과목A" + suffix, "2025-2");
		createCourse(token, "페이지과목B" + suffix, "2026-2");
		createCourse(token, "페이지과목C" + suffix, "2026-1");

		mockMvc.perform(get("/api/courses").header("Authorization", token)
				.param("keyword", "페이지과목").param("size", "2"))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].term").value("2026-2"))
			.andExpect(jsonPath("$.items[1].term").value("2026-1"))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(3)))
			.andExpect(jsonPath("$.hasNext").value(true));
	}

	// --- 학교 경계 ---

	@Test
	void 다른_학교의_과목은_검색에도_안_나오고_접근하면_없는_과목처럼_404다() throws Exception {
		jdbcTemplate.update("INSERT INTO schools (code, name, email_domain, created_at, updated_at) "
			+ "VALUES (?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))", "T" + suffix, "타교" + suffix, suffix + ".example.com");
		School other = schoolRepository.findByCode("T" + suffix).orElseThrow();
		Course foreign = courseRepository.saveAndFlush(
			Course.create(other.getId(), null, "타교과목" + suffix, "타교과목" + suffix, "2026-2", userId));

		mockMvc.perform(get("/api/courses").header("Authorization", token).param("keyword", "타교과목" + suffix))
			.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/courses/" + foreign.getId()).header("Authorization", token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(get("/api/courses/" + foreign.getId() + "/units").header("Authorization", token))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/courses/" + foreign.getId() + "/disciple")
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"마리\"}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void 없는_과목은_404다() throws Exception {
		mockMvc.perform(get("/api/courses/2000000000").header("Authorization", token))
			.andExpect(status().isNotFound());
	}

	// --- 주차 ---

	@Test
	void 주차는_숫자_순서로_나오고_공백만_다른_이름은_기존_id와_함께_409다() throws Exception {
		long courseId = idOf(createCourse(token, "주차과목" + suffix, "2026-2"));
		String path = "/api/courses/" + courseId + "/units";
		long second = 0;
		for (String title : new String[] {"10주차", "2주차", "1주차"}) {
			ResultActions created = mockMvc.perform(post(path)
					.header("Authorization", token)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"title\": \"" + title + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value(title))
				.andExpect(jsonPath("$.myNoteCount").value(0))
				.andExpect(jsonPath("$.battleReady").value(false));
			if (title.equals("2주차")) {
				second = idOf(created);
			}
		}

		mockMvc.perform(post(path)
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"2 주차\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("UNIT_DUPLICATE"))
			.andExpect(jsonPath("$.details.existingUnitId").value(second));

		mockMvc.perform(get(path).header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].title").value("1주차"))
			.andExpect(jsonPath("$[1].title").value("2주차"))
			.andExpect(jsonPath("$[2].title").value("10주차"));
	}

	@Test
	void 주차는_같은_학교_학생이_함께_쓴다() throws Exception {
		long courseId = idOf(createCourse(token, "공유과목" + suffix, "2026-2"));
		String friendToken = bearer(newUser("b"));

		mockMvc.perform(post("/api/courses/" + courseId + "/units")
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"1주차\"}"))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/courses/" + courseId + "/units").header("Authorization", friendToken))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].title").value("1주차"));
		mockMvc.perform(post("/api/courses/" + courseId + "/units")
				.header("Authorization", friendToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"1 주차\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("UNIT_DUPLICATE"));
	}

	// --- 제자 ---

	@Test
	void 제자를_만들면_수강_등록이_되고_내_과목에_나타난다() throws Exception {
		long courseId = idOf(createCourse(token, "제자과목" + suffix, "2026-2"));
		String path = "/api/courses/" + courseId + "/disciple";

		mockMvc.perform(get(path).header("Authorization", token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DISCIPLE_NOT_FOUND"));
		mockMvc.perform(get("/api/me/courses").header("Authorization", token))
			.andExpect(jsonPath("$.length()").value(0));

		mockMvc.perform(post(path)
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"마리\", \"characterKey\": \"cat\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.courseId").value(courseId))
			.andExpect(jsonPath("$.name").value("마리"))
			.andExpect(jsonPath("$.characterKey").value("cat"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty());

		mockMvc.perform(post(path)
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"또마리\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DISCIPLE_ALREADY_EXISTS"));

		mockMvc.perform(get(path).header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("마리"));
		mockMvc.perform(get("/api/me/courses").header("Authorization", token))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(courseId))
			.andExpect(jsonPath("$[0].disciple.name").value("마리"));
		mockMvc.perform(get("/api/courses/" + courseId).header("Authorization", token))
			.andExpect(jsonPath("$.hasMyDisciple").value(true));
	}

	@Test
	void 제자는_학생마다_따로이고_남의_제자는_보이지_않는다() throws Exception {
		long courseId = idOf(createCourse(token, "남의제자" + suffix, "2026-2"));
		String friendToken = bearer(newUser("b"));
		String path = "/api/courses/" + courseId + "/disciple";

		mockMvc.perform(post(path)
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"마리\"}"))
			.andExpect(status().isCreated());

		mockMvc.perform(get(path).header("Authorization", friendToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DISCIPLE_NOT_FOUND"));
		mockMvc.perform(post(path)
				.header("Authorization", friendToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"친구제자\"}"))
			.andExpect(status().isCreated());
		mockMvc.perform(get(path).header("Authorization", token))
			.andExpect(jsonPath("$.name").value("마리"));
	}

	@Test
	void 제자_이름이_비었거나_너무_길면_400이다() throws Exception {
		long courseId = idOf(createCourse(token, "이름검사" + suffix, "2026-2"));
		String path = "/api/courses/" + courseId + "/disciple";

		mockMvc.perform(post(path).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"  \"}"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post(path).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + "가".repeat(31) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	// --- 로그인 ---

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/me/courses")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/courses/1/units")).andExpect(status().isUnauthorized());
	}

}
