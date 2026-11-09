package com.aidisciple.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.ai.AiPurpose;
import com.aidisciple.ai.LlmClient;
import com.aidisciple.ai.log.AiCallLog;
import com.aidisciple.ai.log.AiCallLogRepository;
import com.aidisciple.ai.log.AiCallRecord;
import com.aidisciple.ai.log.AiProvider;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.battle.repository.BattleAnswerRepository;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.Disciple;
import com.aidisciple.domain.course.entity.Unit;
import com.aidisciple.domain.course.repository.CourseRepository;
import com.aidisciple.domain.course.repository.DiscipleRepository;
import com.aidisciple.domain.course.repository.UnitRepository;
import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.entity.JobTriggerType;
import com.aidisciple.domain.question.entity.Question;
import com.aidisciple.domain.question.entity.QuestionGenerationJob;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.entity.QuestionValidationLog;
import com.aidisciple.domain.question.entity.ValidationStage;
import com.aidisciple.domain.question.repository.QuestionGenerationJobRepository;
import com.aidisciple.domain.question.repository.QuestionRepository;
import com.aidisciple.domain.question.repository.QuestionValidationLogRepository;
import com.aidisciple.domain.review.entity.Report;
import com.aidisciple.domain.review.entity.ReportKind;
import com.aidisciple.domain.review.entity.ReportReasonType;
import com.aidisciple.domain.review.entity.ReportStatus;
import com.aidisciple.domain.review.repository.ReportRepository;
import com.aidisciple.global.security.UserRole;

/**
 * 관리자 API(X-01~X-05, X-07, X-08, X-10)를 DB 까지 이어서 확인한다. 권한은 SecurityConfig 의 경로 규칙이 막는다.
 * 각 테스트는 트랜잭션으로 감싸 끝나면 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminApiTest {

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

	@Autowired
	QuestionRepository questionRepository;

	@Autowired
	QuestionGenerationJobRepository jobRepository;

	@Autowired
	QuestionValidationLogRepository validationLogRepository;

	@Autowired
	BattleRepository battleRepository;

	@Autowired
	BattleParticipantRepository participantRepository;

	@Autowired
	BattleQuestionRepository battleQuestionRepository;

	@Autowired
	BattleAnswerRepository answerRepository;

	@Autowired
	ReportRepository reportRepository;

	@Autowired
	AiCallLogRepository aiCallLogRepository;

	@MockitoBean
	LlmClient llmClient;

	private String suffix;
	private long schoolId;
	private User admin;
	private User alice;
	private User bob;
	private long courseId;
	private long unitId;

	@BeforeEach
	void 관리자와_두_학생_과목_주차를_만든다() {
		suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		schoolId = schoolRepository.findByCode("MJU").orElseThrow().getId();
		admin = newUser("adm", UserRole.ADMIN);
		alice = newUser("a", UserRole.STUDENT);
		bob = newUser("b", UserRole.STUDENT);
		String courseName = "자료구조" + suffix;
		courseId = courseRepository.saveAndFlush(Course.create(schoolId, null, courseName, courseName, "2026-2", alice.getId())).getId();
		unitId = unitRepository.saveAndFlush(Unit.create(courseId, "3주차", "3주차", alice.getId())).getId();
	}

	// --- 권한 ---

	@Test
	void 학생은_관리자_API에_들어올_수_없다() throws Exception {
		mockMvc.perform(get("/api/admin/questions").header("Authorization", bearer(alice)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/stats/ai-calls").header("Authorization", bearer(alice)))
			.andExpect(status().isForbidden());
	}

	// --- X-03·X-04·X-05 문항 검수 ---

	@Test
	void 검수_대기_문항만_골라_보고_통과시키면_VERIFIED가_되며_UNJUDGED로는_되돌릴_수_없다() throws Exception {
		long pending = newQuestion("스택이란?", QuestionStatus.UNJUDGED);
		newQuestion("큐란?", QuestionStatus.VERIFIED);

		mockMvc.perform(get("/api/admin/questions").param("unitId", String.valueOf(unitId)).param("status", "UNJUDGED")
				.header("Authorization", bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].id").value(pending))
			.andExpect(jsonPath("$.items[0].usedCount").value(0))
			.andExpect(jsonPath("$.items[0].correctRate").value(nullValue()));

		changeStatus(pending, "VERIFIED")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("VERIFIED"))
			.andExpect(jsonPath("$.reviewedBy").value(admin.getId()))
			.andExpect(jsonPath("$.referenceAnswer").value("후입선출"))
			.andExpect(jsonPath("$.answers[0]").value("후입선출"));

		changeStatus(pending, "UNJUDGED")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void 두_사람이_같은_문항을_하나는_맞히고_하나는_틀리면_정답률은_절반이다() throws Exception {
		long questionId = newQuestion("스택이란?", QuestionStatus.VERIFIED);
		Battle battle = Battle.openRoom(courseId, unitId, 3, alice.getId(), "ZZZZZ4", Instant.now());
		ReflectionTestUtils.setField(battle, "status", BattleStatus.FINISHED);
		ReflectionTestUtils.setField(battle, "activeRoomCode", null);
		long battleId = battleRepository.saveAndFlush(battle).getId();
		long battleQuestionId = battleQuestionRepository.saveAndFlush(BattleQuestion.of(battleId, questionId, 1, null)).getId();
		answer(battleId, battleQuestionId, alice, "마리", Verdict.CORRECT);
		answer(battleId, battleQuestionId, bob, "토리", Verdict.WRONG);

		mockMvc.perform(get("/api/admin/questions/{id}", questionId).header("Authorization", bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.usedCount").value(1))
			.andExpect(jsonPath("$.answeredCount").value(2))
			.andExpect(jsonPath("$.correctRate").value(0.5));
	}

	// --- X-07·X-08 신고 처리 ---

	@Test
	void 신고를_받아들여_문항을_폐기했다가_되살릴_수_있고_OPEN으로는_처리할_수_없다() throws Exception {
		long questionId = newQuestion("스택이란?", QuestionStatus.UNJUDGED);
		Question question = questionRepository.findById(questionId).orElseThrow();
		question.markUnderReview();
		long reportId = reportRepository.saveAndFlush(Report.ofAnswer(alice.getId(), ReportKind.QUESTION, questionId, null,
			ReportReasonType.AMBIGUOUS, "답이 두 개일 수 있어요")).getId();

		mockMvc.perform(get("/api/admin/reports").param("status", "OPEN").header("Authorization", bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(reportId))
			.andExpect(jsonPath("$.items[0].questionStem").value("스택이란?" + suffix));

		handle(reportId, "{\"status\": \"ACCEPTED\", \"resolutionNote\": \"문항 폐기\", \"questionAction\": \"RETIRE\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.handledBy").value(admin.getId()));
		assertThat(questionRepository.findById(questionId).orElseThrow().getStatus()).isEqualTo(QuestionStatus.RETIRED);

		handle(reportId, "{\"status\": \"REJECTED\", \"questionAction\": \"RESTORE\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"));
		assertThat(questionRepository.findById(questionId).orElseThrow().getStatus()).isEqualTo(QuestionStatus.VERIFIED);

		handle(reportId, "{\"status\": \"OPEN\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	// --- X-01·X-02 문항 생성 작업 ---

	@Test
	void 작업_진행에서_단계별_탈락_수를_보고_없는_주차나_작업은_404다() throws Exception {
		long jobId = jobRepository.saveAndFlush(QuestionGenerationJob.queue(unitId, JobTriggerType.ADMIN, admin.getId(), 5)).getId();
		validationLogRepository.saveAndFlush(QuestionValidationLog.of(jobId, null, ValidationStage.EVIDENCE_CHECK, false, "{}", null));
		validationLogRepository.saveAndFlush(QuestionValidationLog.of(jobId, null, ValidationStage.EVIDENCE_CHECK, false, "{}", null));
		validationLogRepository.saveAndFlush(QuestionValidationLog.of(jobId, null, ValidationStage.LEAK_TEST, false, "{}", null));

		mockMvc.perform(get("/api/admin/question-jobs/{id}", jobId).header("Authorization", bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("QUEUED"))
			.andExpect(jsonPath("$.targetCount").value(5))
			.andExpect(jsonPath("$.rejectedByStage.EVIDENCE_CHECK").value(2))
			.andExpect(jsonPath("$.rejectedByStage.LEAK_TEST").value(1));

		mockMvc.perform(get("/api/admin/question-jobs/{id}", Long.MAX_VALUE).header("Authorization", bearer(admin)))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/admin/units/{id}/question-jobs", Long.MAX_VALUE).header("Authorization", bearer(admin)))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/admin/units/{id}/question-jobs", unitId).header("Authorization", bearer(admin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"targetCount\": 100}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	// --- X-10 AI 호출 통계 ---

	@Test
	void 오늘의_AI_호출이_용도별로_잡히고_범위_밖의_기간은_400이다() throws Exception {
		aiCallLogRepository.saveAndFlush(AiCallLog.from(new AiCallRecord(AiPurpose.GRADE_BATCH, AiProvider.GEMINI,
			"test-model", "grade-batch", "v1", 100, 20, 300, true, null, null, null, null)));

		mockMvc.perform(get("/api/admin/stats/ai-calls").param("days", "3").header("Authorization", bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zone").value("Asia/Seoul"))
			.andExpect(jsonPath("$.days.length()").value(3))
			.andExpect(jsonPath("$.days[0].total").value(greaterThanOrEqualTo(1)))
			.andExpect(jsonPath("$.days[0].byPurpose.GRADE_BATCH").value(greaterThanOrEqualTo(1)));

		mockMvc.perform(get("/api/admin/stats/ai-calls").param("days", "0").header("Authorization", bearer(admin)))
			.andExpect(status().isBadRequest());
	}

	// --- X-06 문항 직접 추가 ---

	@Test
	void 관리자가_직접_만든_문항은_MANUAL로_바로_VERIFIED이고_정답_정보가_없으면_400이다() throws Exception {
		mockMvc.perform(post("/api/admin/questions").header("Authorization", bearer(admin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"unitId": %d, "stem": "스택의 동작 방식은?", "gradingMode": "ANSWER_LIST", "answers": ["후입선출", "LIFO"],
					 "referenceAnswer": "후입선출", "evidenceText": "스택은 나중에 넣은 것이 먼저 나온다."}
					""".formatted(unitId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.source").value("MANUAL"))
			.andExpect(jsonPath("$.status").value("VERIFIED"))
			.andExpect(jsonPath("$.leakTestPassed").value(false))
			.andExpect(jsonPath("$.answers[1]").value("LIFO"))
			.andExpect(jsonPath("$.reviewedBy").value(admin.getId()));

		mockMvc.perform(post("/api/admin/questions").header("Authorization", bearer(admin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"unitId": %d, "stem": "스택이란?", "gradingMode": "ANSWER_LIST",
					 "referenceAnswer": "후입선출", "evidenceText": "근거"}
					""".formatted(unitId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));

		mockMvc.perform(post("/api/admin/questions").header("Authorization", bearer(admin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"unitId": %d, "stem": "스택이란?", "gradingMode": "KEY_ELEMENTS",
					 "keyElements": [{"label": "후입선출"}, {"label": "push"}], "requiredElements": 3,
					 "referenceAnswer": "후입선출", "evidenceText": "근거"}
					""".formatted(unitId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	// --- X-09·X-11·X-12 통계·내보내기 ---

	@Test
	void 모두_틀린_문항은_너무_어렵고_모두_맞힌_문항은_너무_쉽다() throws Exception {
		long hard = newQuestion("트리란?", QuestionStatus.VERIFIED);
		long easy = newQuestion("큐란?", QuestionStatus.VERIFIED);
		twoPlayerBattle(hard, Verdict.WRONG, Verdict.UNKNOWN, easy, Verdict.CORRECT, Verdict.CORRECT);

		mockMvc.perform(get("/api/admin/stats/questions").param("unitId", String.valueOf(unitId)).param("minAnswers", "2")
				.header("Authorization", bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tooHard.length()").value(1))
			.andExpect(jsonPath("$.tooHard[0].questionId").value(hard))
			.andExpect(jsonPath("$.tooHard[0].correctRate").value(0.0))
			.andExpect(jsonPath("$.tooEasy.length()").value(1))
			.andExpect(jsonPath("$.tooEasy[0].questionId").value(easy))
			.andExpect(jsonPath("$.tooEasy[0].correctRate").value(1.0));
	}

	@Test
	void 사용_지표는_오늘_끝난_배틀과_참가한_학생을_센다() throws Exception {
		long questionId = newQuestion("스택이란?", QuestionStatus.VERIFIED);
		long other = newQuestion("큐란?", QuestionStatus.VERIFIED);
		twoPlayerBattle(questionId, Verdict.CORRECT, Verdict.WRONG, other, Verdict.WRONG, Verdict.CORRECT);

		mockMvc.perform(get("/api/admin/stats/usage").param("days", "1").header("Authorization", bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zone").value("Asia/Seoul"))
			.andExpect(jsonPath("$.activeStudents").value(greaterThanOrEqualTo(2)))
			.andExpect(jsonPath("$.rankedBattlesFinished").value(greaterThanOrEqualTo(1)));

		mockMvc.perform(get("/api/admin/stats/usage").param("days", "0").header("Authorization", bearer(admin)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 채점_CSV에는_답마다_한_줄이_나오고_학생_정보는_없다() throws Exception {
		long questionId = newQuestion("스택이란?", QuestionStatus.VERIFIED);
		long other = newQuestion("=1+1 큐란?", QuestionStatus.VERIFIED);
		twoPlayerBattle(questionId, Verdict.CORRECT, Verdict.WRONG, other, Verdict.WRONG, Verdict.CORRECT);

		mockMvc.perform(get("/api/admin/exports/battle-answers.csv").param("days", "1")
				.param("courseId", String.valueOf(courseId)).header("Authorization", bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("text/csv"))			.andExpect(content().string(containsString("battle_id,mode,finished_at")))
			.andExpect(content().string(containsString("스택이란?" + suffix)))
			// 수식으로 실행되지 않게 ' 를 붙인다 (CSV 인젝션 방지)
			.andExpect(content().string(containsString("'=1+1 큐란?" + suffix)));

		mockMvc.perform(get("/api/admin/exports/battle-answers.csv").param("days", "0").header("Authorization", bearer(admin)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	// --- 준비 도우미 ---


	/** 앨리스·밥이 두 문항을 푼, 방금 끝난 랭킹 배틀. 판정은 [앨리스, 밥] 순서로 문항마다 준다. */
	private void twoPlayerBattle(long first, Verdict aliceFirst, Verdict bobFirst,
								 long second, Verdict aliceSecond, Verdict bobSecond) {
		Battle battle = Battle.openRoom(courseId, unitId, 3, alice.getId(), "ZZZZZ5", Instant.now());
		ReflectionTestUtils.setField(battle, "status", BattleStatus.FINISHED);
		ReflectionTestUtils.setField(battle, "activeRoomCode", null);
		ReflectionTestUtils.setField(battle, "finishedAt", Instant.now());
		long battleId = battleRepository.saveAndFlush(battle).getId();
		long q1 = battleQuestionRepository.saveAndFlush(BattleQuestion.of(battleId, first, 1, null)).getId();
		long q2 = battleQuestionRepository.saveAndFlush(BattleQuestion.of(battleId, second, 2, null)).getId();
		long aliceParticipant = participant(battleId, alice, "마리");
		long bobParticipant = participant(battleId, bob, "토리");
		gradedAnswer(aliceParticipant, q1, aliceFirst);
		gradedAnswer(bobParticipant, q1, bobFirst);
		gradedAnswer(aliceParticipant, q2, aliceSecond);
		gradedAnswer(bobParticipant, q2, bobSecond);
	}

	private long participant(long battleId, User user, String discipleName) {
		long discipleId = discipleRepository.saveAndFlush(Disciple.create(user.getId(), courseId, discipleName, null)).getId();
		return participantRepository.saveAndFlush(
			BattleParticipant.joined(battleId, user.getId(), discipleId, user.getId().equals(alice.getId()), Instant.now())).getId();
	}

	private void gradedAnswer(long participantId, long battleQuestionId, Verdict verdict) {
		BattleAnswer answer = BattleAnswer.solved(participantId, battleQuestionId, "답", false, "[]");
		ReflectionTestUtils.setField(answer, "verdict", verdict);
		answerRepository.saveAndFlush(answer);
	}

	private org.springframework.test.web.servlet.ResultActions changeStatus(long questionId, String status) throws Exception {
		return mockMvc.perform(patch("/api/admin/questions/{id}/status", questionId).header("Authorization", bearer(admin))
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"%s\"}".formatted(status)));
	}

	private org.springframework.test.web.servlet.ResultActions handle(long reportId, String body) throws Exception {
		return mockMvc.perform(patch("/api/admin/reports/{id}", reportId).header("Authorization", bearer(admin))
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** 이 사용자의 제자로 참가해 그 문항에 판정이 정해진 답 하나를 남긴다 */
	private void answer(long battleId, long battleQuestionId, User user, String discipleName, Verdict verdict) {
		long discipleId = discipleRepository.saveAndFlush(Disciple.create(user.getId(), courseId, discipleName, null)).getId();
		BattleParticipant participant = participantRepository.saveAndFlush(
			BattleParticipant.joined(battleId, user.getId(), discipleId, user.getId().equals(alice.getId()), Instant.now()));
		BattleAnswer answer = BattleAnswer.solved(participant.getId(), battleQuestionId, "답", false, "[]");
		ReflectionTestUtils.setField(answer, "verdict", verdict);
		answerRepository.saveAndFlush(answer);
	}

	private User newUser(String tag, UserRole role) {
		return userRepository.saveAndFlush(User.create(schoolId, null, "sub-" + tag + suffix,
			tag + suffix + "@mju.ac.kr", tag + suffix, role, Instant.now()));
	}

	private long newQuestion(String stem, QuestionStatus status) {
		return questionRepository.saveAndFlush(Question.fromAi(courseId, unitId, stem + suffix, GradingMode.ANSWER_LIST,
			"[\"후입선출\"]", null, null, "후입선출", "근거 문장", status, null)).getId();
	}

	private String bearer(User user) {
		return "Bearer " + jwtProvider.issueAccessToken(user.getId(), user.getRole());
	}

}
