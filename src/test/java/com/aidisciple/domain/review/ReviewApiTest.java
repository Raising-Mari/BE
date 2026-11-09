package com.aidisciple.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.ai.LlmClient;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleMode;
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
import com.aidisciple.domain.question.entity.Question;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.repository.QuestionRepository;
import com.aidisciple.domain.review.entity.ReviewCause;
import com.aidisciple.domain.review.repository.ReviewEntryRepository;
import com.aidisciple.domain.teaching.entity.ChatMessage;
import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.repository.ChatMessageRepository;
import com.aidisciple.domain.teaching.repository.NoteEntryRepository;
import com.aidisciple.global.security.UserRole;

/**
 * 복기(R-01·R-02)·신고(R-03)·정답 보기(R-11)를 DB 까지 이어서 확인한다. 채점은 WP9 가 하므로 끝난 배틀과 판정을 직접 만든다.
 * 각 테스트는 트랜잭션으로 감싸 끝나면 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewApiTest {

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
	ReviewEntryRepository reviewEntryRepository;

	@MockitoBean
	LlmClient llmClient;

	private String suffix;
	private long schoolId;
	private User alice;
	private User bob;
	private long courseId;
	private long unitId;
	private long aliceDiscipleId;
	private long noteId;
	private List<Long> questionIds;
	private long battleId;
	/** 앨리스의 답 id (문항 순서대로) */
	private List<Long> answerIds;

	@BeforeEach
	void 앨리스가_세_문항을_푼_끝난_랭킹_배틀을_만든다() {
		suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		schoolId = schoolRepository.findByCode("MJU").orElseThrow().getId();
		alice = newUser("a");
		bob = newUser("b");
		String courseName = "자료구조" + suffix;
		courseId = courseRepository.saveAndFlush(Course.create(schoolId, null, courseName, courseName, "2026-2", alice.getId())).getId();
		unitId = unitRepository.saveAndFlush(Unit.create(courseId, "3주차", "3주차", alice.getId())).getId();
		aliceDiscipleId = discipleRepository.saveAndFlush(Disciple.create(alice.getId(), courseId, "마리", null)).getId();
		noteId = addNote(aliceDiscipleId, "스택은 먼저 넣은 것이 먼저 나온다.");
		questionIds = List.of(newQuestion("스택이란?"), newQuestion("큐란?"), newQuestion("트리란?"));
		// 1번: 노트를 인용해 틀림(원인 제안 NOTE_WRONG), 2번: 맞힘, 3번: 지시문 의심으로 0점
		battleId = finishedBattle();
	}

	// --- R-01 복기 ---

	@Test
	void 정답_보기_전에는_모범_답과_근거가_없고_맞힌_문항도_함께_나온다() throws Exception {
		review(alice)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.answersRevealed").value(false))
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].seq").value(1))
			.andExpect(jsonPath("$.items[0].question.stem").value("스택이란?" + suffix))
			.andExpect(jsonPath("$.items[0].question.referenceAnswer").doesNotExist())
			.andExpect(jsonPath("$.items[0].evidence").doesNotExist())
			.andExpect(jsonPath("$.items[0].disciple.verdict").value("WRONG"))
			.andExpect(jsonPath("$.items[0].usedNotes[0].id").value(noteId))
			.andExpect(jsonPath("$.items[0].usedNotes[0].content").value("스택은 먼저 넣은 것이 먼저 나온다."))
			.andExpect(jsonPath("$.items[0].causeSuggestion").value("NOTE_WRONG"))
			.andExpect(jsonPath("$.items[0].myReview").value(nullValue()))
			.andExpect(jsonPath("$.items[0].practiceAvailable").value(true))
			.andExpect(jsonPath("$.items[1].disciple.verdict").value("CORRECT"))
			.andExpect(jsonPath("$.items[1].causeSuggestion").value(nullValue()))
			.andExpect(jsonPath("$.items[2].disciple.verdict").value("ZEROED_INJECTION"))
			.andExpect(jsonPath("$.items[2].disciple.injectionFlag").value(true));
	}

	// --- R-11 정답 보기 ---

	@Test
	void 정답_보기는_두_번_눌러도_같은_시각이고_그_뒤에는_모범_답과_근거가_나오며_연습은_막힌다() throws Exception {
		String first = reveal(alice).andExpect(status().isOk())
			.andExpect(jsonPath("$.answersRevealed").value(true))
			.andReturn().getResponse().getContentAsString();
		String second = reveal(alice).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat((String) JsonPath.read(second, "$.revealedAt")).isEqualTo(JsonPath.read(first, "$.revealedAt"));

		review(alice)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.answersRevealed").value(true))
			.andExpect(jsonPath("$.items[0].question.referenceAnswer").value("후입선출"))
			.andExpect(jsonPath("$.items[0].evidence.text").value("근거 문장"))
			.andExpect(jsonPath("$.items[0].practiceAvailable").value(false));

		mockMvc.perform(post("/api/battles/practice").header("Authorization", bearer(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"sourceBattleId\": %d}".formatted(battleId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRACTICE_ANSWERS_REVEALED"));
	}

	// --- R-02 원인·메모 ---

	@Test
	void 원인과_메모를_저장하면_복기에_나오고_다시_저장하면_같은_줄을_고친다() throws Exception {
		saveReview(alice, answerIds.get(0), "{\"userCause\": \"NOT_IN_NOTES\", \"studyMemo\": \"후입선출로 다시 가르치기\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userCause").value("NOT_IN_NOTES"))
			.andExpect(jsonPath("$.studyMemo").value("후입선출로 다시 가르치기"));

		saveReview(alice, answerIds.get(0), "{\"userCause\": \"NOTE_WRONG\", \"sourceNote\": \"강의자료 3장\", \"reasonNote\": \"교재 정의\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userCause").value("NOTE_WRONG"))
			.andExpect(jsonPath("$.studyMemo").value(nullValue()));

		assertThat(reviewEntryRepository.findByBattleAnswerIdIn(answerIds)).hasSize(1);
		review(alice)
			.andExpect(jsonPath("$.items[0].myReview.userCause").value("NOTE_WRONG"))
			.andExpect(jsonPath("$.items[0].myReview.sourceNote").value("강의자료 3장"));
	}

	@Test
	void 원인을_빼고_저장하면_400() throws Exception {
		saveReview(alice, answerIds.get(0), "{\"studyMemo\": \"메모만\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	// --- R-03 신고 ---

	@Test
	void 문항을_신고하면_UNDER_REVIEW가_되고_같은_신고는_한_번만_되며_이의제기는_따로_된다() throws Exception {
		report(alice, answerIds.get(0), "QUESTION", "AMBIGUOUS")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("QUESTION"))
			.andExpect(jsonPath("$.status").value("OPEN"));
		assertThat(questionRepository.findById(questionIds.get(0)).orElseThrow().getStatus()).isEqualTo(QuestionStatus.UNDER_REVIEW);
		review(alice).andExpect(jsonPath("$.items[0].practiceAvailable").value(false));

		report(alice, answerIds.get(0), "QUESTION", "WRONG_ANSWER")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REPORT_DUPLICATE"));

		report(alice, answerIds.get(2), "GRADING", "GRADING_DISPUTE")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("GRADING"));
		// 이의제기는 문항을 빼지 않는다
		assertThat(questionRepository.findById(questionIds.get(2)).orElseThrow().getStatus()).isEqualTo(QuestionStatus.UNJUDGED);
	}

	// --- 권한·상태 ---

	@Test
	void 남의_배틀과_남의_답은_없는_것과_같은_404다() throws Exception {
		review(bob).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		reveal(bob).andExpect(status().isNotFound());
		saveReview(bob, answerIds.get(0), "{\"userCause\": \"OTHER\"}").andExpect(status().isNotFound());
		report(bob, answerIds.get(0), "QUESTION", "OTHER").andExpect(status().isNotFound());
		assertThat(questionRepository.findById(questionIds.get(0)).orElseThrow().getStatus()).isEqualTo(QuestionStatus.UNJUDGED);
	}

	@Test
	void 끝나지_않은_배틀은_BATTLE_NOT_FINISHED_연습_배틀은_INVALID_INPUT() throws Exception {
		Battle battle = battleRepository.findById(battleId).orElseThrow();
		ReflectionTestUtils.setField(battle, "status", BattleStatus.RUNNING);
		battleRepository.saveAndFlush(battle);
		review(alice).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BATTLE_NOT_FINISHED"));
		reveal(alice).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BATTLE_NOT_FINISHED"));

		ReflectionTestUtils.setField(battle, "status", BattleStatus.FINISHED);
		ReflectionTestUtils.setField(battle, "mode", BattleMode.PRACTICE);
		battleRepository.saveAndFlush(battle);
		review(alice).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		saveReview(alice, answerIds.get(0), "{\"userCause\": \"OTHER\"}")
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	// --- 준비 도우미 ---

	/** 앨리스만 참가한 끝난 랭킹 배틀. 1번 답은 노트를 인용해 틀림, 2번은 맞힘, 3번은 지시문 의심 0점. */
	private long finishedBattle() {
		Battle battle = Battle.openRoom(courseId, unitId, 3, alice.getId(), "ZZZZZ3", Instant.now());
		ReflectionTestUtils.setField(battle, "status", BattleStatus.FINISHED);
		ReflectionTestUtils.setField(battle, "activeRoomCode", null);
		battle = battleRepository.saveAndFlush(battle);
		BattleParticipant mine = participantRepository.saveAndFlush(
			BattleParticipant.joined(battle.getId(), alice.getId(), aliceDiscipleId, true, Instant.now()));
		List<Verdict> verdicts = List.of(Verdict.WRONG, Verdict.CORRECT, Verdict.ZEROED_INJECTION);
		answerIds = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			BattleQuestion question = battleQuestionRepository.saveAndFlush(
				BattleQuestion.of(battle.getId(), questionIds.get(i), i + 1, null));
			String cited = i == 0 ? "[" + noteId + "]" : "[]";
			BattleAnswer answer = BattleAnswer.solved(mine.getId(), question.getId(), "답", false, cited);
			ReflectionTestUtils.setField(answer, "verdict", verdicts.get(i));
			if (i == 0) {
				ReflectionTestUtils.setField(answer, "causeSuggestion", ReviewCause.NOTE_WRONG);
			}
			if (i == 2) {
				ReflectionTestUtils.setField(answer, "injectionFlag", true);
			}
			answerIds.add(answerRepository.saveAndFlush(answer).getId());
		}
		return battle.getId();
	}

	private ResultActions review(User user) throws Exception {
		return mockMvc.perform(get("/api/battles/{id}/review", battleId).header("Authorization", bearer(user)));
	}

	private ResultActions reveal(User user) throws Exception {
		return mockMvc.perform(post("/api/battles/{id}/reveal-answers", battleId).header("Authorization", bearer(user)));
	}

	private ResultActions saveReview(User user, long answerId, String body) throws Exception {
		return mockMvc.perform(put("/api/battle-answers/{id}/review", answerId).header("Authorization", bearer(user))
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions report(User user, long answerId, String kind, String reasonType) throws Exception {
		return mockMvc.perform(post("/api/battle-answers/{id}/reports", answerId).header("Authorization", bearer(user))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\": \"%s\", \"reasonType\": \"%s\"}".formatted(kind, reasonType)));
	}

	private User newUser(String tag) {
		return userRepository.saveAndFlush(User.create(schoolId, null, "sub-" + tag + suffix,
			tag + suffix + "@mju.ac.kr", tag + suffix, UserRole.STUDENT, Instant.now()));
	}

	private long addNote(long discipleId, String content) {
		ChatMessage message = chatMessageRepository.saveAndFlush(
			ChatMessage.student(discipleId, unitId, 1, content, UUID.randomUUID().toString()));
		return noteEntryRepository.saveAndFlush(
			NoteEntry.create(discipleId, unitId, content, content, message.getId(), null, 1, "{}")).getId();
	}

	private long newQuestion(String stem) {
		return questionRepository.saveAndFlush(Question.fromAi(courseId, unitId, stem + suffix, GradingMode.ANSWER_LIST,
			"[\"후입선출\"]", null, null, "후입선출", "근거 문장", QuestionStatus.UNJUDGED, null)).getId();
	}

	private String bearer(User user) {
		return "Bearer " + jwtProvider.issueAccessToken(user.getId(), user.getRole());
	}

}
