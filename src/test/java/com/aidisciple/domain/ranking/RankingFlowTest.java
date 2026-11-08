package com.aidisciple.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import com.aidisciple.domain.auth.entity.Department;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.DepartmentRepository;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleStatus;
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
import com.aidisciple.domain.grading.service.GradingRunner;
import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.entity.Question;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.repository.QuestionRepository;
import com.aidisciple.domain.ranking.entity.EloRating;
import com.aidisciple.domain.ranking.repository.EloHistoryRepository;
import com.aidisciple.domain.ranking.repository.EloRatingRepository;
import com.aidisciple.domain.ranking.service.EloService;
import com.aidisciple.domain.teaching.entity.ChatMessage;
import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.repository.ChatMessageRepository;
import com.aidisciple.domain.teaching.repository.NoteEntryRepository;
import com.aidisciple.global.security.UserRole;

/**
 * 풀이가 끝난 랭킹 배틀이 채점되어 FINISHED 가 될 때 같은 트랜잭션에서 Elo 가 반영되고, 그 결과를 순위·전적·성장폭·이력·배틀 응답으로
 * 보는 흐름을 DB 까지 이어서 확인한다. AI 는 가짜로 바꾸고 채점은 비동기 이벤트 대신 {@link GradingRunner} 를 직접 불러 돌린다.
 * 각 테스트는 트랜잭션으로 감싸 되돌린다. 앨리스(정답)가 밥(오답)을 이긴다: 앨리스 1016, 밥 984.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RankingFlowTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	UserRepository userRepository;

	@Autowired
	SchoolRepository schoolRepository;

	@Autowired
	DepartmentRepository departmentRepository;

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
	EloRatingRepository ratingRepository;

	@Autowired
	EloHistoryRepository historyRepository;

	@Autowired
	GradingRunner gradingRunner;

	@Autowired
	EloService eloService;

	@MockitoBean
	LlmClient llmClient;

	private String suffix;
	private User alice;
	private User bob;
	private long courseId;
	private long unitId;
	private long battleId;

	@BeforeEach
	void 풀이가_끝난_랭킹_배틀을_만든다() {
		suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		long schoolId = schoolRepository.findByCode("MJU").orElseThrow().getId();
		Department department = departmentRepository.findAll().get(0);
		alice = newUser(schoolId, department.getId(), "a");
		bob = newUser(schoolId, department.getId(), "b");
		String courseName = "자료구조" + suffix;
		courseId = courseRepository.saveAndFlush(Course.create(schoolId, null, courseName, courseName, "2026-2", alice.getId())).getId();
		unitId = unitRepository.saveAndFlush(Unit.create(courseId, "3주차", "3주차", alice.getId())).getId();
		long aliceDisciple = discipleRepository.saveAndFlush(Disciple.create(alice.getId(), courseId, "마리", null)).getId();
		long bobDisciple = discipleRepository.saveAndFlush(Disciple.create(bob.getId(), courseId, "토리", null)).getId();
		long aliceNote = addNote(aliceDisciple, "스택은 후입선출 구조다");
		long bobNote = addNote(bobDisciple, "큐는 선입선출");
		long questionId = questionRepository.saveAndFlush(Question.fromAi(courseId, unitId, "스택의 입출력 순서는?" + suffix,
			GradingMode.ANSWER_LIST, "[\"후입선출\"]", null, null, "후입선출",
			"스택은 가장 나중에 들어간 자료가 가장 먼저 나오는 후입선출 구조이다.", QuestionStatus.UNJUDGED, null)).getId();

		Battle battle = Battle.openRoom(courseId, unitId, 1, alice.getId(), "ZZZZZ6", Instant.now());
		ReflectionTestUtils.setField(battle, "status", BattleStatus.RUNNING);
		ReflectionTestUtils.setField(battle, "activeRoomCode", null);
		battleId = battleRepository.saveAndFlush(battle).getId();
		BattleQuestion question = battleQuestionRepository.saveAndFlush(BattleQuestion.of(battleId, questionId, 1, null));
		long aliceParticipant = solvedParticipant(alice, aliceDisciple, true, aliceNote);
		long bobParticipant = solvedParticipant(bob, bobDisciple, false, bobNote);
		answerRepository.saveAndFlush(BattleAnswer.solved(aliceParticipant, question.getId(), "후입선출", false, NoteIdsJson.write(List.of(aliceNote))));
		answerRepository.saveAndFlush(BattleAnswer.solved(bobParticipant, question.getId(), "선입선출", false, NoteIdsJson.write(List.of(bobNote))));
	}

	@Test
	void 채점이_끝나면_같은_트랜잭션에서_이긴_쪽이_오르고_진_쪽이_내린다() {
		gradingRunner.run(battleId);

		assertThat(battleRepository.findById(battleId).orElseThrow().getStatus()).isEqualTo(BattleStatus.FINISHED);
		EloRating winner = ratingRepository.findByUserIdAndCourseId(alice.getId(), courseId).orElseThrow();
		EloRating loser = ratingRepository.findByUserIdAndCourseId(bob.getId(), courseId).orElseThrow();
		assertThat(winner.getRating()).isEqualTo(1016);
		assertThat(winner.getWins()).isEqualTo(1);
		assertThat(winner.getGamesPlayed()).isEqualTo(1);
		assertThat(loser.getRating()).isEqualTo(984);
		assertThat(loser.getLosses()).isEqualTo(1);
		assertThat(historyRepository.findByBattleIdIn(List.of(battleId))).hasSize(2);
	}

	@Test
	void 같은_배틀을_다시_반영해도_Elo가_두_번_바뀌지_않는다() {
		gradingRunner.run(battleId);

		gradingRunner.run(battleId);   // 같은 이벤트가 두 번 와도
		boolean applied = eloService.apply(battleId);   // 복구가 다시 불러도

		assertThat(applied).isFalse();
		assertThat(ratingRepository.findByUserIdAndCourseId(alice.getId(), courseId).orElseThrow().getRating()).isEqualTo(1016);
		assertThat(ratingRepository.findByUserIdAndCourseId(alice.getId(), courseId).orElseThrow().getGamesPlayed()).isEqualTo(1);
		assertThat(historyRepository.findByBattleIdIn(List.of(battleId))).hasSize(2);
	}

	@Test
	void 배틀_응답에_Elo_변화가_실린다() throws Exception {
		gradingRunner.run(battleId);

		mockMvc.perform(get("/api/battles/{id}", battleId).header("Authorization", bearer(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FINISHED"))
			.andExpect(jsonPath("$.summary.eloDelta").value(16));
		mockMvc.perform(get("/api/battles/{id}/result", battleId).header("Authorization", bearer(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.elo.before").value(1000))
			.andExpect(jsonPath("$.elo.after").value(984))
			.andExpect(jsonPath("$.elo.delta").value(-16));
	}

	@Test
	void 순위에_두_사람이_레이팅_순으로_나오고_내_순위도_준다() throws Exception {
		gradingRunner.run(battleId);

		mockMvc.perform(get("/api/rankings").param("courseId", String.valueOf(courseId)).header("Authorization", bearer(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.items[0].rank").value(1))
			.andExpect(jsonPath("$.items[0].userId").value(alice.getId()))
			.andExpect(jsonPath("$.items[0].rating").value(1016))
			.andExpect(jsonPath("$.items[0].wins").value(1))
			.andExpect(jsonPath("$.items[0].nickname").value(alice.getNickname()))
			.andExpect(jsonPath("$.items[1].userId").value(bob.getId()))
			.andExpect(jsonPath("$.items[1].rating").value(984))
			.andExpect(jsonPath("$.me.rank").value(2))
			.andExpect(jsonPath("$.me.userId").value(bob.getId()));
	}

	@Test
	void 학과_순위는_내_학과가_기본이다() throws Exception {
		gradingRunner.run(battleId);

		mockMvc.perform(get("/api/rankings").param("courseId", String.valueOf(courseId)).param("scope", "DEPARTMENT")
				.header("Authorization", bearer(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].userId").value(alice.getId()))
			.andExpect(jsonPath("$.items[0].departmentName").isString())
			.andExpect(jsonPath("$.me.rank").value(1));
	}

	@Test
	void 내_Elo와_전적과_성장폭과_이력을_본다() throws Exception {
		gradingRunner.run(battleId);

		mockMvc.perform(get("/api/me/ratings").header("Authorization", bearer(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].course.name").value("자료구조" + suffix))
			.andExpect(jsonPath("$[0].rating").value(1016))
			.andExpect(jsonPath("$[0].wins").value(1));
		mockMvc.perform(get("/api/me/records").header("Authorization", bearer(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].opponent.id").value(bob.getId()))
			.andExpect(jsonPath("$[0].wins").value(1))
			.andExpect(jsonPath("$[0].losses").value(0));
		mockMvc.perform(get("/api/me/records/{opponentId}/battles", bob.getId()).header("Authorization", bearer(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].battleId").value(battleId))
			.andExpect(jsonPath("$.items[0].result").value("WIN"));
		mockMvc.perform(get("/api/rankings/growth").param("courseId", String.valueOf(courseId)).header("Authorization", bearer(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].userId").value(alice.getId()))
			.andExpect(jsonPath("$.items[0].delta").value(16))
			.andExpect(jsonPath("$.me.rank").value(2))
			.andExpect(jsonPath("$.me.delta").value(-16));
		mockMvc.perform(get("/api/me/ratings/history").header("Authorization", bearer(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].battleId").value(battleId))
			.andExpect(jsonPath("$.items[0].opponent.id").value(bob.getId()))
			.andExpect(jsonPath("$.items[0].ratingBefore").value(1000))
			.andExpect(jsonPath("$.items[0].ratingAfter").value(1016))
			.andExpect(jsonPath("$.items[0].delta").value(16));
	}

	@Test
	void 배틀을_한_적_없으면_내_순위와_Elo가_비어_있다() throws Exception {
		User newcomer = newUser(schoolRepository.findByCode("MJU").orElseThrow().getId(), null, "c");

		mockMvc.perform(get("/api/rankings").param("courseId", String.valueOf(courseId)).header("Authorization", bearer(newcomer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.me").value(nullValue()));
		mockMvc.perform(get("/api/me/ratings").header("Authorization", bearer(newcomer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/me/records").header("Authorization", bearer(newcomer)))
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 없는_과목의_순위는_404이고_성장폭_기간이_잘못되면_400이다() throws Exception {
		mockMvc.perform(get("/api/rankings").param("courseId", "999999999").header("Authorization", bearer(alice)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/rankings/growth").param("courseId", String.valueOf(courseId)).param("days", "0")
				.header("Authorization", bearer(alice)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	// --- 도우미 ---

	private long solvedParticipant(User user, long discipleId, boolean creator, long noteId) {
		BattleParticipant participant = BattleParticipant.joined(battleId, user.getId(), discipleId, creator, Instant.now());
		participant.markReady(NoteIdsJson.write(List.of(noteId)), 1, false, Instant.now());
		participant.claimSolve();
		participant.markSolved(1);
		return participantRepository.saveAndFlush(participant).getId();
	}

	private long addNote(long discipleId, String content) {
		int turnNo = chatMessageRepository.findMaxTurnNo(discipleId, unitId) + 1;
		ChatMessage message = chatMessageRepository.saveAndFlush(
			ChatMessage.student(discipleId, unitId, turnNo, content, UUID.randomUUID().toString()));
		return noteEntryRepository.saveAndFlush(NoteEntry.create(discipleId, unitId, content, content, message.getId(), null, 1, "{}")).getId();
	}

	private User newUser(long schoolId, Long departmentId, String tag) {
		return userRepository.saveAndFlush(User.create(schoolId, departmentId, "sub-" + tag + suffix,
			tag + suffix + "@mju.ac.kr", tag + suffix, UserRole.STUDENT, Instant.now()));
	}

	private String bearer(User user) {
		return "Bearer " + jwtProvider.issueAccessToken(user.getId(), user.getRole());
	}

}
