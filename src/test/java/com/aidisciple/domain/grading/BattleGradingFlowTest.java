package com.aidisciple.domain.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.ai.LlmClient;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleResult;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.battle.logic.NoteIdsJson;
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
import com.aidisciple.domain.grading.service.GradeBatchResponse;
import com.aidisciple.domain.grading.service.GradingRunner;
import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.entity.Question;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.repository.QuestionRepository;
import com.aidisciple.domain.review.entity.ReviewCause;
import com.aidisciple.domain.teaching.entity.ChatMessage;
import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.repository.ChatMessageRepository;
import com.aidisciple.domain.teaching.repository.NoteEntryRepository;
import com.aidisciple.global.security.UserRole;

/**
 * 풀이가 끝난 배틀을 채점해 FINISHED 로 만들고 B-07 로 결과를 보는 흐름을 DB 까지 이어서 확인한다.
 * 문항의 정답·핵심 요소 JSON 읽기, battle_answers 의 채점 컬럼 저장, 참가자 합계·승패, 결과 응답을 실제 DB 로 본다.
 * AI 는 가짜로 바꾸고, 채점은 비동기 이벤트 대신 {@link GradingRunner} 를 직접 불러 돌린다. 각 테스트는 트랜잭션으로 감싸 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BattleGradingFlowTest {

	private static final String EVIDENCE = "스택은 가장 나중에 들어간 자료가 가장 먼저 나오는 후입선출 구조이다.";
	private static final String ELEMENTS_JSON = "[{\"label\":\"alpha\",\"aliases\":[]},{\"label\":\"bravo\",\"aliases\":[]},"
		+ "{\"label\":\"charlie\",\"aliases\":[]},{\"label\":\"delta\",\"aliases\":[]},{\"label\":\"echo\",\"aliases\":[]}]";

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
	ChatMessageRepository chatMessageRepository;

	@Autowired
	NoteEntryRepository noteEntryRepository;

	@Autowired
	QuestionRepository questionRepository;

	@Autowired
	BattleRepository battleRepository;

	@Autowired
	BattleParticipantRepository participantRepository;

	@Autowired
	BattleQuestionRepository battleQuestionRepository;

	@Autowired
	BattleAnswerRepository answerRepository;

	@Autowired
	GradingRunner gradingRunner;

	@MockitoBean
	LlmClient llmClient;

	private String suffix;
	private User alice;
	private User bob;
	private long courseId;
	private long unitId;
	private long q1Id;
	private long q2Id;
	private long battleId;
	private long aliceParticipantId;
	private long bobParticipantId;
	private long bobAmbiguousAnswerId;
	private long bobWrongAnswerId;

	@BeforeEach
	void 풀이가_끝난_배틀을_만든다() {
		suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		long schoolId = schoolRepository.findByCode("MJU").orElseThrow().getId();
		alice = newUser(schoolId, "a");
		bob = newUser(schoolId, "b");
		String courseName = "자료구조" + suffix;
		courseId = courseRepository.saveAndFlush(Course.create(schoolId, null, courseName, courseName, "2026-2", alice.getId())).getId();
		unitId = unitRepository.saveAndFlush(Unit.create(courseId, "3주차", "3주차", alice.getId())).getId();
		long aliceDisciple = discipleRepository.saveAndFlush(Disciple.create(alice.getId(), courseId, "마리", null)).getId();
		long bobDisciple = discipleRepository.saveAndFlush(Disciple.create(bob.getId(), courseId, "토리", null)).getId();

		long aliceNote = addNote(aliceDisciple, "스택은 후입선출 구조다");
		long bobStackNote = addNote(bobDisciple, "큐는 선입선출");
		long bobElementsNote = addNote(bobDisciple, "alpha bravo charlie delta echo");

		q1Id = questionRepository.saveAndFlush(Question.fromAi(courseId, unitId, "스택의 입출력 순서는?" + suffix, GradingMode.ANSWER_LIST,
			"[\"후입선출\",\"LIFO\"]", null, null, "후입선출", EVIDENCE, QuestionStatus.UNJUDGED, null)).getId();
		q2Id = questionRepository.saveAndFlush(Question.fromAi(courseId, unitId, "스택을 설명하라" + suffix, GradingMode.KEY_ELEMENTS,
			null, ELEMENTS_JSON, 4, "alpha bravo charlie delta echo", EVIDENCE, QuestionStatus.UNJUDGED, null)).getId();

		Battle battle = Battle.openRoom(courseId, unitId, 3, alice.getId(), "ZZZZZ3", Instant.now());
		ReflectionTestUtils.setField(battle, "status", BattleStatus.RUNNING);
		ReflectionTestUtils.setField(battle, "activeRoomCode", null);
		battleId = battleRepository.saveAndFlush(battle).getId();
		BattleQuestion bq1 = battleQuestionRepository.saveAndFlush(BattleQuestion.of(battleId, q1Id, 1, null));
		BattleQuestion bq2 = battleQuestionRepository.saveAndFlush(BattleQuestion.of(battleId, q2Id, 2, null));

		aliceParticipantId = solvedParticipant(alice, aliceDisciple, true, List.of(aliceNote));
		bobParticipantId = solvedParticipant(bob, bobDisciple, false, List.of(bobStackNote, bobElementsNote));
		saveAnswer(aliceParticipantId, bq1, "후입선출", false, List.of(aliceNote));         // 정답
		saveAnswer(aliceParticipantId, bq2, null, true, List.of());                          // 모름
		bobWrongAnswerId = saveAnswer(bobParticipantId, bq1, "선입선출", false, List.of(bobStackNote));   // 오답
		bobAmbiguousAnswerId = saveAnswer(bobParticipantId, bq2, "alpha bravo", false, List.of(bobElementsNote));   // 2/5: 애매함
	}

	@Test
	void 채점하면_답마다_판정과_점수가_채워지고_승패가_정해져_FINISHED가_된다() throws Exception {
		when(llmClient.generateJson(any(), eq(GradeBatchResponse.class))).thenReturn(new GradeBatchResponse(List.of(
			new GradeBatchResponse.LlmResult(bobAmbiguousAnswerId, "PARTIAL", "일부만 맞음"))));

		gradingRunner.run(battleId);

		assertThat(battleRepository.findById(battleId).orElseThrow().getStatus()).isEqualTo(BattleStatus.FINISHED);
		BattleAnswer wrong = answerRepository.findById(bobWrongAnswerId).orElseThrow();
		assertThat(wrong.getVerdict()).isEqualTo(Verdict.WRONG);
		assertThat(wrong.getFinalScore()).isEqualByComparingTo("-0.50");
		assertThat(wrong.getCauseSuggestion()).isEqualTo(ReviewCause.NOT_TAUGHT);
		assertThat(wrong.getGradingDetailJson()).contains("\"stage\":\"CODE\"");
		BattleAnswer partial = answerRepository.findById(bobAmbiguousAnswerId).orElseThrow();
		assertThat(partial.getVerdict()).isEqualTo(Verdict.PARTIAL);
		assertThat(partial.getFinalScore()).isEqualByComparingTo("0.50");
		assertThat(partial.getGradingDetailJson()).contains("일부만 맞음");

		BattleParticipant aliceParticipant = participantRepository.findById(aliceParticipantId).orElseThrow();
		BattleParticipant bobParticipant = participantRepository.findById(bobParticipantId).orElseThrow();
		assertThat(aliceParticipant.getTotalScore()).isEqualByComparingTo("1.00");
		assertThat(aliceParticipant.getCorrectCount()).isEqualTo(1);
		assertThat(aliceParticipant.getUnknownCount()).isEqualTo(1);
		assertThat(aliceParticipant.getResult()).isEqualTo(BattleResult.WIN);
		assertThat(bobParticipant.getTotalScore()).isEqualByComparingTo("0.00");
		assertThat(bobParticipant.getResult()).isEqualTo(BattleResult.LOSE);
		assertThat(answerRepository.findByParticipantIdIn(List.of(aliceParticipantId, bobParticipantId)))
			.extracting(BattleAnswer::getVerdict).doesNotContain(Verdict.PENDING);
	}

	@Test
	void 채점이_끝나면_B_07과_B_03에서_결과를_본다() throws Exception {
		when(llmClient.generateJson(any(), eq(GradeBatchResponse.class))).thenReturn(new GradeBatchResponse(List.of(
			new GradeBatchResponse.LlmResult(bobAmbiguousAnswerId, "PARTIAL", "일부만 맞음"))));
		gradingRunner.run(battleId);

		mockMvc.perform(get("/api/battles/{id}/result", battleId).header("Authorization", bearer(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("RANKED"))
			.andExpect(jsonPath("$.result").value("WIN"))
			.andExpect(jsonPath("$.me.totalScore").value(1.0))
			.andExpect(jsonPath("$.me.correct").value(1))
			.andExpect(jsonPath("$.opponent.totalScore").value(0.0))
			.andExpect(jsonPath("$.questions.length()").value(2))
			.andExpect(jsonPath("$.questions[0].myVerdict").value("CORRECT"))
			.andExpect(jsonPath("$.questions[0].opponentVerdict").value("WRONG"))
			.andExpect(jsonPath("$.questions[0].opponentAnswerText").doesNotExist())   // 공개 동의 전에는 상대 답 원문이 없다
			.andExpect(jsonPath("$.questions[1].opponentVerdict").value("PARTIAL"));
		mockMvc.perform(get("/api/battles/{id}/result", battleId).header("Authorization", bearer(bob)))
			.andExpect(jsonPath("$.result").value("LOSE"));
		mockMvc.perform(get("/api/battles/{id}", battleId).header("Authorization", bearer(alice)))
			.andExpect(jsonPath("$.status").value("FINISHED"))
			.andExpect(jsonPath("$.summary.myScore").value(1.0))
			.andExpect(jsonPath("$.summary.opponentScore").value(0.0))
			.andExpect(jsonPath("$.summary.result").value("WIN"));
	}

	@Test
	void 같은_배틀을_두_번_채점해도_AI는_한_번만_부르고_결과는_그대로다() throws Exception {
		when(llmClient.generateJson(any(), eq(GradeBatchResponse.class))).thenReturn(new GradeBatchResponse(List.of(
			new GradeBatchResponse.LlmResult(bobAmbiguousAnswerId, "WRONG", "틀림"))));

		gradingRunner.run(battleId);
		gradingRunner.run(battleId);   // 같은 이벤트가 두 번 와도

		verify(llmClient, times(1)).generateJson(any(), eq(GradeBatchResponse.class));
		assertThat(answerRepository.findById(bobAmbiguousAnswerId).orElseThrow().getVerdict()).isEqualTo(Verdict.WRONG);
	}

	@Test
	void AI_판정이_실패하면_그_답만_UNJUDGED로_0점이고_배틀은_끝난다() throws Exception {
		when(llmClient.generateJson(any(), eq(GradeBatchResponse.class)))
			.thenThrow(new com.aidisciple.ai.AiException(com.aidisciple.global.error.ErrorCode.AI_UNAVAILABLE));

		gradingRunner.run(battleId);

		BattleAnswer unjudged = answerRepository.findById(bobAmbiguousAnswerId).orElseThrow();
		assertThat(unjudged.getVerdict()).isEqualTo(Verdict.UNJUDGED);
		assertThat(unjudged.getFinalScore()).isEqualByComparingTo("0");
		assertThat(battleRepository.findById(battleId).orElseThrow().getStatus()).isEqualTo(BattleStatus.FINISHED);
		assertThat(answerRepository.findById(bobWrongAnswerId).orElseThrow().getVerdict()).isEqualTo(Verdict.WRONG);   // 코드로 가린 답은 그대로
	}

	@Test
	void 끝나지_않은_배틀의_결과는_409이고_참가자가_아니면_404다() throws Exception {
		mockMvc.perform(get("/api/battles/{id}/result", battleId).header("Authorization", bearer(alice)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BATTLE_NOT_FINISHED"));

		gradingRunner.run(battleId);
		User stranger = newUser(schoolRepository.findByCode("MJU").orElseThrow().getId(), "c");
		mockMvc.perform(get("/api/battles/{id}/result", battleId).header("Authorization", bearer(stranger)))
			.andExpect(status().isNotFound());
	}

	@Test
	void 풀이가_끝나지_않은_참가자가_있으면_채점하지_않는다() {
		BattleParticipant bobParticipant = participantRepository.findById(bobParticipantId).orElseThrow();
		ReflectionTestUtils.setField(bobParticipant, "solveStatus", com.aidisciple.domain.battle.entity.SolveStatus.SOLVING);
		participantRepository.saveAndFlush(bobParticipant);

		gradingRunner.run(battleId);

		assertThat(battleRepository.findById(battleId).orElseThrow().getStatus()).isEqualTo(BattleStatus.RUNNING);
		verify(llmClient, never()).generateJson(any(), any());
	}

	// --- 도우미 ---

	private long solvedParticipant(User user, long discipleId, boolean creator, List<Long> noteIds) {
		BattleParticipant participant = BattleParticipant.joined(battleId, user.getId(), discipleId, creator, Instant.now());
		participant.markReady(NoteIdsJson.write(noteIds), noteIds.size(), false, Instant.now());
		participant.claimSolve();
		participant.markSolved(1);
		return participantRepository.saveAndFlush(participant).getId();
	}

	private long saveAnswer(long participantId, BattleQuestion question, String text, boolean unknown, List<Long> citedNoteIds) {
		return answerRepository.saveAndFlush(BattleAnswer.solved(participantId, question.getId(), text, unknown,
			NoteIdsJson.write(citedNoteIds))).getId();
	}

	private long addNote(long discipleId, String content) {
		int turnNo = chatMessageRepository.findMaxTurnNo(discipleId, unitId) + 1;
		ChatMessage message = chatMessageRepository.saveAndFlush(
			ChatMessage.student(discipleId, unitId, turnNo, content, UUID.randomUUID().toString()));
		return noteEntryRepository.saveAndFlush(NoteEntry.create(discipleId, unitId, content, content, message.getId(), null, 1, "{}")).getId();
	}

	private User newUser(long schoolId, String tag) {
		return userRepository.saveAndFlush(User.create(schoolId, null, "sub-" + tag + suffix,
			tag + suffix + "@mju.ac.kr", tag + suffix, UserRole.STUDENT, Instant.now()));
	}

	private String bearer(User user) {
		return "Bearer " + jwtProvider.issueAccessToken(user.getId(), user.getRole());
	}

}
