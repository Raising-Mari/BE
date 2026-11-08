package com.aidisciple.domain.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.ai.LlmClient;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.Unit;
import com.aidisciple.domain.course.repository.CourseRepository;
import com.aidisciple.domain.course.repository.UnitRepository;
import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.entity.JobStatus;
import com.aidisciple.domain.question.entity.JobTriggerType;
import com.aidisciple.domain.question.entity.Question;
import com.aidisciple.domain.question.entity.QuestionGenerationJob;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.repository.QuestionEvidenceRepository;
import com.aidisciple.domain.question.repository.QuestionGenerationJobRepository;
import com.aidisciple.domain.question.repository.QuestionRepository;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.domain.question.service.QuestionGenerateResponse;
import com.aidisciple.domain.question.service.QuestionGenerateResponse.GeneratedQuestion;
import com.aidisciple.domain.question.service.QuestionJobRunner;
import com.aidisciple.domain.question.service.SolveLlmResponse;
import com.aidisciple.global.security.UserRole;

/**
 * 문제 은행을 DB 까지 이어서 확인한다: DONE 자료가 연결된 주차 → 작업 실행 → 문항·근거·검증 로그 → Q-01·문항 선택.
 * AI 는 가짜(LlmClient 목)다. 자료는 업로드 흐름(비동기) 대신 DB 에 바로 넣는다. 테스트가 끝나면 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QuestionBankFlowTest {

	private static final String SENTENCE_1 = "스택은 나중에 넣은 데이터가 먼저 나오는 후입선출 구조이다.";
	private static final String SENTENCE_2 = "큐는 먼저 넣은 데이터가 먼저 나오는 선입선출 구조이다.";

	@Autowired MockMvc mockMvc;
	@Autowired JwtProvider jwtProvider;
	@Autowired JdbcTemplate jdbc;
	@Autowired UserRepository userRepository;
	@Autowired SchoolRepository schoolRepository;
	@Autowired CourseRepository courseRepository;
	@Autowired UnitRepository unitRepository;
	@Autowired QuestionGenerationJobRepository jobRepository;
	@Autowired QuestionRepository questionRepository;
	@Autowired QuestionEvidenceRepository evidenceRepository;
	@Autowired QuestionJobRunner jobRunner;
	@Autowired QuestionBankService questionBankService;

	@MockitoBean
	LlmClient llmClient;

	private User student;
	private long unitId;
	private long materialId;

	@BeforeEach
	void DONE_자료가_연결된_주차를_만든다() {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		long schoolId = schoolRepository.findByCode("MJU").orElseThrow().getId();
		student = userRepository.saveAndFlush(User.create(schoolId, null, "sub-q" + suffix,
			"q" + suffix + "@mju.ac.kr", "q" + suffix, UserRole.STUDENT, Instant.now()));
		String courseName = "자료구조" + suffix;
		long courseId = courseRepository.saveAndFlush(
			Course.create(schoolId, null, courseName, courseName, "2026-2", student.getId())).getId();
		unitId = unitRepository.saveAndFlush(Unit.create(courseId, "3주차", "3주차", student.getId())).getId();

		jdbc.update("""
			insert into materials (course_id, uploader_id, title, source_type, status, char_count, sentence_count,
			  consent_agreed_at, processed_at, created_at, updated_at)
			values (?, ?, '공개 예시 자료', 'TEXT', 'DONE', 60, 2, now(6), now(6), now(6), now(6))
			""", courseId, student.getId());
		materialId = jdbc.queryForObject("select last_insert_id()", Long.class);
		insertSentence(1, SENTENCE_1);
		insertSentence(2, SENTENCE_2);
		jdbc.update("insert into material_units (material_id, unit_id, created_at) values (?, ?, now(6))",
			materialId, unitId);
	}

	@Test
	void 작업을_돌리면_통과한_문항만_근거와_함께_UNJUDGED로_저장되고_탈락은_로그에만_남는다() throws Exception {
		when(llmClient.generateJson(any(), eq(QuestionGenerateResponse.class))).thenReturn(
			new QuestionGenerateResponse(List.of(
				new GeneratedQuestion("나중에 넣은 데이터가 먼저 나오는 구조는?", "ANSWER_LIST", List.of("스택"), List.of(),
					"스택", "나중에 넣은 데이터가 먼저 나오는 후입선출 구조", List.of(1)),
				new GeneratedQuestion("자료에 없는 내용을 묻는 문항입니다?", "ANSWER_LIST", List.of("트리"), List.of(),
					"트리", "트리는 계층 구조이다", List.of(1)))));
		when(llmClient.generateJson(any(), eq(SolveLlmResponse.class))).thenReturn(
			new SolveLlmResponse(List.of(new SolveLlmResponse.LlmAnswer(1L, "", true, List.of()))));
		long jobId = jobRepository.saveAndFlush(
			QuestionGenerationJob.queue(unitId, JobTriggerType.ADMIN, null, 10)).getId();

		jobRunner.run(jobId);

		// 라운드 3번(목표 10개를 못 채움): 1라운드에 1개 통과, 2·3라운드는 같은 문항이라 중복으로 탈락
		QuestionGenerationJob job = jobRepository.findById(jobId).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
		assertThat(job.getGeneratedCount()).isEqualTo(6);
		assertThat(job.getPassedCount()).isEqualTo(1);
		assertThat(job.getRejectedCount()).isEqualTo(5);

		List<Question> questions = questionRepository.findByUnitIdAndStatusIn(unitId, List.of(QuestionStatus.UNJUDGED));
		assertThat(questions).singleElement().satisfies(question -> {
			assertThat(question.getGradingMode()).isEqualTo(GradingMode.ANSWER_LIST);
			assertThat(question.getAnswersJson()).contains("스택");
			assertThat(question.getEvidenceText()).isEqualTo(SENTENCE_1);
			assertThat(question.getConceptId()).isNull();
		});

		List<Long> sentenceIds = jdbc.queryForList(
			"select id from material_sentences where material_id = ? order by seq", Long.class, materialId);
		assertThat(evidenceRepository.findQuestionIdsBySentenceIds(sentenceIds))
			.containsExactly(questions.get(0).getId());

		Integer logCount = jdbc.queryForObject("select count(*) from question_validation_logs where job_id = ?",
			Integer.class, jobId);
		assertThat(logCount).isEqualTo(6);

		mockMvc.perform(get("/api/units/{unitId}/battle-readiness", unitId)
				.param("questionCount", "3")
				.header("Authorization", "Bearer " + jwtProvider.issueAccessToken(student.getId(), student.getRole())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.availableForMe").value(1))
			.andExpect(jsonPath("$.required").value(3))
			.andExpect(jsonPath("$.ready").value(false));

		assertThat(questionBankService.pickForBattle(unitId, List.of(student.getId()), 1, 7L))
			.containsExactly(questions.get(0).getId());
		assertThat(questionBankService.pickForBattle(unitId, List.of(student.getId()), 2, 7L)).isEmpty();
	}

	@Test
	void 연결된_자료가_없으면_작업은_시작하지_않고_실패로_닫힌다() {
		jdbc.update("delete from material_units where material_id = ?", materialId);
		long jobId = jobRepository.saveAndFlush(
			QuestionGenerationJob.queue(unitId, JobTriggerType.ADMIN, null, 10)).getId();

		jobRunner.run(jobId);

		assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isNotEqualTo(JobStatus.DONE);
	}

	private void insertSentence(int seq, String text) {
		jdbc.update("""
			insert into material_sentences (material_id, seq, text, normalized_text, text_hash, created_at)
			values (?, ?, ?, ?, ?, now(6))
			""", materialId, seq, text, text, "0".repeat(64));
	}

}
