package com.aidisciple.domain.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.material.entity.MaterialSentence;
import com.aidisciple.domain.material.event.MaterialProcessedEvent;
import com.aidisciple.domain.material.event.MaterialUnitsChangedEvent;
import com.aidisciple.domain.material.repository.MaterialSentenceRepository;
import com.aidisciple.domain.material.service.MaterialContent;
import com.aidisciple.domain.material.service.MaterialProcessingService;
import com.aidisciple.domain.material.service.MaterialProcessor;
import com.aidisciple.domain.material.service.MaterialStateService;
import com.aidisciple.global.security.UserRole;

/**
 * 강의자료 API 와 처리 흐름을 DB 까지 이어서 확인한다. 비동기 처리 스레드({@link MaterialProcessor})는 가짜로 막아 두고,
 * 처리 로직은 테스트 안에서 직접(동기로) 불러 같은 트랜잭션 안에서 결과를 본다. 각 테스트는 끝나면 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class MaterialApiTest {

	private static final String TEXT = "스택은 나중에 넣은 것이 먼저 나오는 구조이다. 큐는 먼저 넣은 것이 먼저 나오는 구조이다.\n"
		+ "스택은 함수 호출과 되돌리기에 쓰인다.";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	UserRepository userRepository;

	@Autowired
	SchoolRepository schoolRepository;

	@Autowired
	MaterialSentenceRepository sentenceRepository;

	@Autowired
	MaterialProcessingService processingService;

	@Autowired
	MaterialStateService stateService;

	@Autowired
	ApplicationEvents events;

	@MockitoBean
	MaterialProcessor processor;

	private String suffix;
	private long schoolId;
	private String token;
	private long courseId;

	@BeforeEach
	void 학생과_과목을_만든다() throws Exception {
		suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		schoolId = schoolRepository.findByCode("MJU").orElseThrow().getId();
		token = bearer(newUser("a"));
		courseId = idOf(mockMvc.perform(post("/api/courses")
			.header("Authorization", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"자료과목" + suffix + "\", \"term\": \"2026-2\"}")));
	}

	private User newUser(String tag) {
		return userRepository.saveAndFlush(User.create(schoolId, null, "sub-" + tag + suffix,
			tag + suffix + "@mju.ac.kr", tag + suffix, UserRole.STUDENT, Instant.now()));
	}

	private String bearer(User user) {
		return "Bearer " + jwtProvider.issueAccessToken(user.getId(), user.getRole());
	}

	private long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private ResultActions uploadText(String authorization, long course, String title, String text, boolean consent) throws Exception {
		return mockMvc.perform(post("/api/courses/" + course + "/materials/text")
			.header("Authorization", authorization)
			.contentType(MediaType.APPLICATION_JSON)
			.content(String.format("{\"title\": \"%s\", \"text\": \"%s\", \"consentAgreed\": %s}", title, text, consent)));
	}

	private long uploadedTextMaterialId() throws Exception {
		ResultActions accepted = uploadText(token, courseId, "텍스트 자료", TEXT.replace("\n", "\\n"), true).andExpect(status().isAccepted());
		return ((Number) JsonPath.read(accepted.andReturn().getResponse().getContentAsString(), "$.materialId")).longValue();
	}

	private long createUnit(String title) throws Exception {
		return idOf(mockMvc.perform(post("/api/courses/" + courseId + "/units")
			.header("Authorization", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\": \"" + title + "\"}")));
	}

	private ResultActions uploadPdf(String authorization, MockMultipartFile file, Boolean consent) throws Exception {
		var request = multipart("/api/courses/" + courseId + "/materials/file")
			.file(file)
			.param("title", "PDF 자료")
			.header("Authorization", authorization);
		if (consent != null) {
			request.param("consentAgreed", consent.toString());
		}
		return mockMvc.perform(request);
	}

	// --- 접수 (M-01·M-02) ---

	@Test
	void 텍스트를_접수하면_202와_PENDING을_주고_처리_작업을_큐에_넣는다() throws Exception {
		long materialId = uploadedTextMaterialId();

		verify(processor).processAsync(anyLong(), any(MaterialContent.class));
		mockMvc.perform(get("/api/materials/" + materialId).header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.title").value("텍스트 자료"))
			.andExpect(jsonPath("$.sentenceCount").value(0))
			.andExpect(jsonPath("$.unitIds.length()").value(0));
	}

	@Test
	void 동의하지_않으면_400이고_아무것도_접수되지_않는다() throws Exception {
		uploadText(token, courseId, "제목", "글", false)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MATERIAL_CONSENT_REQUIRED"));

		MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", PdfFixtures.pdf("hello"));
		uploadPdf(token, pdf, null)   // consentAgreed 를 아예 보내지 않은 경우도 동의하지 않은 것이다
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MATERIAL_CONSENT_REQUIRED"));
		uploadPdf(token, pdf, false)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MATERIAL_CONSENT_REQUIRED"));

		mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", token))
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void PDF를_접수하면_파일_내용을_처리_작업에_넘긴다() throws Exception {
		byte[] pdf = PdfFixtures.pdf("Stack is last in first out.");

		uploadPdf(token, new MockMultipartFile("file", "lecture.pdf", "application/pdf", pdf), true)
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.materialId").isNumber());

		ArgumentCaptor<MaterialContent> content = ArgumentCaptor.forClass(MaterialContent.class);
		verify(processor).processAsync(anyLong(), content.capture());
		assertThat(content.getValue()).isInstanceOfSatisfying(MaterialContent.Pdf.class,
			uploaded -> assertThat(uploaded.bytes()).isEqualTo(pdf));
	}

	@Test
	void PDF가_아닌_파일은_400이다() throws Exception {
		uploadPdf(token, new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()), true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MATERIAL_INVALID_FILE"));
		uploadPdf(token, new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf at all".getBytes()), true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MATERIAL_INVALID_FILE"));
	}

	@Test
	void 붙여넣은_글이_10만_자를_넘으면_413이다() throws Exception {
		uploadText(token, courseId, "제목", "가".repeat(100_001), true)
			.andExpect(status().isContentTooLarge())
			.andExpect(jsonPath("$.code").value("MATERIAL_TOO_LARGE"));
		uploadText(token, courseId, "제목", "가".repeat(100_000), true)
			.andExpect(status().isAccepted());
	}

	@Test
	void 제목이_비면_400이다() throws Exception {
		uploadText(token, courseId, "", "글", true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 없는_과목에는_올릴_수_없다() throws Exception {
		uploadText(token, 2_000_000_000L, "제목", "글", true).andExpect(status().isNotFound());
	}

	// --- 처리 (J-01) ---

	@Test
	void 처리가_끝나면_DONE이고_문장이_순서대로_저장된다() throws Exception {
		long materialId = uploadedTextMaterialId();

		processingService.process(materialId, new MaterialContent.Text(TEXT));

		mockMvc.perform(get("/api/materials/" + materialId).header("Authorization", token))
			.andExpect(jsonPath("$.status").value("DONE"))
			.andExpect(jsonPath("$.sentenceCount").value(3))
			.andExpect(jsonPath("$.failReason").doesNotExist());
		List<MaterialSentence> sentences = sentenceRepository.findByMaterialIdOrderBySeq(materialId);
		assertThat(sentences).extracting(MaterialSentence::getSeq).containsExactly(1, 2, 3);
		assertThat(sentences.get(0).getText()).isEqualTo("스택은 나중에 넣은 것이 먼저 나오는 구조이다.");
		assertThat(sentences.get(0).getNormalizedText()).isEqualTo("스택은 나중에 넣은 것이 먼저 나오는 구조이다");
		assertThat(sentences.get(0).getTextHash()).hasSize(64);
		assertThat(sentences.get(0).getPageNo()).isNull();
	}

	@Test
	void 실제_PDF의_글을_쪽수와_함께_문장으로_저장한다() throws Exception {
		long materialId = uploadedTextMaterialId();
		byte[] pdf = PdfFixtures.pdf("Stack is last in first out.\nQueue is first in first out.", "Hash tables map keys to values quickly.");

		processingService.process(materialId, new MaterialContent.Pdf(pdf));

		List<MaterialSentence> sentences = sentenceRepository.findByMaterialIdOrderBySeq(materialId);
		assertThat(sentences).extracting(MaterialSentence::getPageNo).containsExactly(1, 1, 2);
		assertThat(sentences.get(2).getText()).contains("Hash tables");
		mockMvc.perform(get("/api/materials/" + materialId).header("Authorization", token))
			.andExpect(jsonPath("$.status").value("DONE"))
			.andExpect(jsonPath("$.sentenceCount").value(3));
	}

	@Test
	void 글자가_없는_스캔본은_NO_TEXT로_실패한다() throws Exception {
		long materialId = uploadedTextMaterialId();

		processingService.process(materialId, new MaterialContent.Pdf(PdfFixtures.pdf("", "")));

		mockMvc.perform(get("/api/materials/" + materialId).header("Authorization", token))
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.failReason").value("NO_TEXT"));
		assertThat(sentenceRepository.countByMaterialId(materialId)).isZero();
	}

	@Test
	void 암호가_걸린_PDF는_PARSE_ERROR로_실패한다() throws Exception {
		long materialId = uploadedTextMaterialId();

		processingService.process(materialId, new MaterialContent.Pdf(PdfFixtures.passwordProtectedPdf("secret")));

		mockMvc.perform(get("/api/materials/" + materialId).header("Authorization", token))
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.failReason").value("PARSE_ERROR"));
	}

	@Test
	void 같은_자료를_두_번_처리해도_문장이_두_번_저장되지_않는다() throws Exception {
		long materialId = uploadedTextMaterialId();

		processingService.process(materialId, new MaterialContent.Text(TEXT));
		processingService.process(materialId, new MaterialContent.Text(TEXT));

		assertThat(sentenceRepository.countByMaterialId(materialId)).isEqualTo(3);
	}

	@Test
	void 처리가_끝나면_이미_연결된_주차와_함께_완료_이벤트가_나간다() throws Exception {
		long materialId = uploadedTextMaterialId();
		long unitId = createUnit("1주차");
		mockMvc.perform(put("/api/materials/" + materialId + "/units")
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"unitIds\": [" + unitId + "]}"))
			.andExpect(status().isOk());

		processingService.process(materialId, new MaterialContent.Text(TEXT));

		assertThat(events.stream(MaterialProcessedEvent.class)).singleElement().satisfies(event -> {
			assertThat(event.materialId()).isEqualTo(materialId);
			assertThat(event.courseId()).isEqualTo(courseId);
			assertThat(event.unitIds()).containsExactly(unitId);
		});
	}

	@Test
	void 서버가_재시작되면_끝나지_못한_자료는_SERVER_RESTART로_닫힌다() throws Exception {
		long materialId = uploadedTextMaterialId();

		stateService.failInterrupted();

		mockMvc.perform(get("/api/materials/" + materialId).header("Authorization", token))
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.failReason").value("SERVER_RESTART"));
	}

	// --- 주차 연결 (M-06) ---

	@Test
	void 주차를_연결하고_바꾸고_풀_수_있다() throws Exception {
		long materialId = uploadedTextMaterialId();
		long week1 = createUnit("1주차");
		long week2 = createUnit("2주차");
		String path = "/api/materials/" + materialId + "/units";

		mockMvc.perform(put(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"unitIds\": [" + week2 + ", " + week1 + "]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.materialId").value(materialId))
			.andExpect(jsonPath("$.unitIds[0]").value(week1))
			.andExpect(jsonPath("$.unitIds[1]").value(week2));
		mockMvc.perform(get("/api/materials/" + materialId).header("Authorization", token))
			.andExpect(jsonPath("$.unitIds.length()").value(2));
		mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", token))
			.andExpect(jsonPath("$[0].unitIds.length()").value(2));

		mockMvc.perform(put(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"unitIds\": [" + week2 + "]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unitIds.length()").value(1));
		mockMvc.perform(put(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"unitIds\": []}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unitIds.length()").value(0));

		List<MaterialUnitsChangedEvent> changes = events.stream(MaterialUnitsChangedEvent.class).toList();
		assertThat(changes).hasSize(3);
		assertThat(changes.get(0).addedUnitIds()).containsExactly(week1, week2);
		assertThat(changes.get(1).removedUnitIds()).containsExactly(week1);
		assertThat(changes.get(2).removedUnitIds()).containsExactly(week2);
		assertThat(changes.get(0).materialDone()).isFalse();
	}

	@Test
	void 다른_과목의_주차는_연결할_수_없다() throws Exception {
		long materialId = uploadedTextMaterialId();
		long otherCourse = idOf(mockMvc.perform(post("/api/courses")
			.header("Authorization", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"다른과목" + suffix + "\", \"term\": \"2026-2\"}")));
		long foreignUnit = idOf(mockMvc.perform(post("/api/courses/" + otherCourse + "/units")
			.header("Authorization", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\": \"1주차\"}")));

		mockMvc.perform(put("/api/materials/" + materialId + "/units").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"unitIds\": [" + foreignUnit + "]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNIT_NOT_IN_COURSE"));
		mockMvc.perform(put("/api/materials/" + materialId + "/units").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"unitIds\": [2000000000]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNIT_NOT_IN_COURSE"));
		assertThat(events.stream(MaterialUnitsChangedEvent.class)).isEmpty();
	}

	@Test
	void unitIds를_보내지_않으면_400이다() throws Exception {
		long materialId = uploadedTextMaterialId();

		mockMvc.perform(put("/api/materials/" + materialId + "/units").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	// --- 접근 ---

	@Test
	void 남의_자료는_조회도_수정도_못_하고_없는_자료처럼_404다() throws Exception {
		long materialId = uploadedTextMaterialId();
		long unitId = createUnit("1주차");
		String friendToken = bearer(newUser("b"));

		mockMvc.perform(get("/api/materials/" + materialId).header("Authorization", friendToken))
			.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/materials/" + materialId + "/units").header("Authorization", friendToken)
				.contentType(MediaType.APPLICATION_JSON).content("{\"unitIds\": [" + unitId + "]}"))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", friendToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 내가_올린_자료_목록은_최근_것이_먼저다() throws Exception {
		uploadText(token, courseId, "먼저", "첫 번째로 올린 글이다.", true).andExpect(status().isAccepted());
		uploadText(token, courseId, "나중", "두 번째로 올린 글이다.", true).andExpect(status().isAccepted());

		mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].title").value("나중"))
			.andExpect(jsonPath("$[1].title").value("먼저"))
			.andExpect(jsonPath("$[0].status").value("PENDING"))
			.andExpect(jsonPath("$[0].createdAt").isNotEmpty());
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/api/materials/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/courses/" + courseId + "/materials")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/courses/" + courseId + "/materials/text")
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

}
