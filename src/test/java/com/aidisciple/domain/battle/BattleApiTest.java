package com.aidisciple.domain.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.entity.SolveStatus;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.battle.repository.BattleAnswerRepository;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.battle.service.BattleExpiryJob;
import com.aidisciple.domain.battle.service.BattleSolveExecutor;
import com.aidisciple.domain.battle.service.BattleSolveStore;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.Disciple;
import com.aidisciple.domain.course.entity.Unit;
import com.aidisciple.domain.course.repository.CourseRepository;
import com.aidisciple.domain.course.repository.DiscipleRepository;
import com.aidisciple.domain.course.repository.UnitRepository;
import com.aidisciple.domain.question.dto.BattleReadinessResponse;
import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.entity.Question;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.repository.QuestionRepository;
import com.aidisciple.domain.question.service.DiscipleSolver;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.domain.teaching.entity.ChatMessage;
import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.repository.ChatMessageRepository;
import com.aidisciple.domain.teaching.repository.NoteEntryRepository;
import com.aidisciple.global.security.UserRole;

/**
 * 배틀 API(B-01~B-06, B-08~B-10)를 DB 까지 이어서 확인한다. 문제 은행(QuestionBankService)과 풀이 실행기·AI 는 가짜로 바꿔
 * 실제 AI 를 부르지 않고 비동기 스레드도 돌리지 않는다 — 풀이는 BattleSolveStore 를 직접 불러 확인한다.
 * 각 테스트는 트랜잭션으로 감싸 끝나면 되돌린다. (문제 은행을 가짜로 두는 것은 재고 보충 작업이 별도 트랜잭션으로
 * 커밋되며 되돌려지지 않는 행을 남기는 것을 피하려는 이유도 있다.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BattleApiTest {

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
	BattleSolveStore solveStore;

	@Autowired
	BattleExpiryJob expiryJob;

	@MockitoBean
	QuestionBankService questionBankService;

	@MockitoBean
	BattleSolveExecutor solveExecutor;

	@MockitoBean
	LlmClient llmClient;

	private String suffix;
	private long schoolId;
	private User alice;
	private User bob;
	private long courseId;
	private long unitId;
	private long aliceDiscipleId;
	private long bobDiscipleId;
	private List<Long> questionIds;

	@BeforeEach
	void 두_학생_과목_주차_제자_노트_문항을_만든다() {
		suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		schoolId = schoolRepository.findByCode("MJU").orElseThrow().getId();
		alice = newUser("a");
		bob = newUser("b");
		String courseName = "자료구조" + suffix;
		courseId = courseRepository.saveAndFlush(Course.create(schoolId, null, courseName, courseName, "2026-2", alice.getId())).getId();
		unitId = unitRepository.saveAndFlush(Unit.create(courseId, "3주차", "3주차", alice.getId())).getId();
		aliceDiscipleId = discipleRepository.saveAndFlush(Disciple.create(alice.getId(), courseId, "마리", null)).getId();
		bobDiscipleId = discipleRepository.saveAndFlush(Disciple.create(bob.getId(), courseId, "토리", null)).getId();
		addNote(aliceDiscipleId, "스택은 나중에 넣은 것이 먼저 나오는 구조다.");
		addNote(bobDiscipleId, "큐는 먼저 넣은 것이 먼저 나온다.");
		questionIds = List.of(newQuestion("스택이란?"), newQuestion("큐란?"), newQuestion("트리란?"));

		when(questionBankService.readiness(anyLong(), eq(unitId), anyInt())).thenReturn(new BattleReadinessResponse(true, 12, 5));
		when(questionBankService.pickForBattle(eq(unitId), anyCollection(), anyInt(), anyLong())).thenReturn(questionIds);
		when(questionBankService.stems(anyCollection())).thenReturn(Map.of(questionIds.get(0), "스택이란?",
			questionIds.get(1), "큐란?", questionIds.get(2), "트리란?"));
		when(questionBankService.practiceUsable(anyCollection())).thenAnswer(invocation -> Set.copyOf(invocation.<java.util.Collection<Long>>getArgument(0)));
	}

	// --- 방 만들기·입장·조회 ---

	@Test
	void 방을_만들고_친구가_코드로_입장하면_둘이_서로의_상태를_본다() throws Exception {
		String roomCode = createRoom(alice);

		join(bob, roomCode.toLowerCase())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("LOBBY"))
			.andExpect(jsonPath("$.mode").value("RANKED"))
			.andExpect(jsonPath("$.course.name").value("자료구조" + suffix))
			.andExpect(jsonPath("$.unit.title").value("3주차"))
			.andExpect(jsonPath("$.participants.length()").value(2))
			.andExpect(jsonPath("$.participants[0].isMe").value(false))
			.andExpect(jsonPath("$.participants[1].isMe").value(true))
			.andExpect(jsonPath("$.summary").value(nullValue()));

		long battleId = battleIdOf(roomCode);
		mockMvc.perform(get("/api/battles/{id}", battleId).header("Authorization", bearer(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("LOBBY"))
			.andExpect(jsonPath("$.participants[0].nickname").value(alice.getNickname()))
			.andExpect(jsonPath("$.participants[0].isMe").value(true))
			.andExpect(jsonPath("$.participants[0].status").value("JOINED"));
	}

	@Test
	void 참가자가_아니면_배틀을_볼_수_없다() throws Exception {
		String roomCode = createRoom(alice);
		User stranger = newUser("c");

		mockMvc.perform(get("/api/battles/{id}", battleIdOf(roomCode)).header("Authorization", bearer(stranger)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void 자기_방에는_입장할_수_없고_없는_코드와_형식이_틀린_코드는_찾을_수_없다() throws Exception {
		String roomCode = createRoom(alice);

		join(alice, roomCode).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BATTLE_SELF_JOIN"));
		join(bob, "ZZZZZZ").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BATTLE_ROOM_NOT_FOUND"));
		join(bob, "abc").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BATTLE_ROOM_NOT_FOUND"));
	}

	@Test
	void 입장_시간이_지난_방은_410이고_만료_작업이_상태를_정리한다() throws Exception {
		String roomCode = createRoom(alice);
		long battleId = battleIdOf(roomCode);
		Battle battle = battleRepository.findById(battleId).orElseThrow();
		ReflectionTestUtils.setField(battle, "expiresAt", Instant.now().minusSeconds(5));
		battleRepository.saveAndFlush(battle);

		join(bob, roomCode).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("BATTLE_ROOM_EXPIRED"));
		mockMvc.perform(get("/api/battles/{id}", battleId).header("Authorization", bearer(alice)))
			.andExpect(jsonPath("$.status").value("EXPIRED"));   // 만료 작업 전이어도 지난 방은 만료로 보인다

		expiryJob.sweep();

		assertThat(battleRepository.findById(battleId).orElseThrow().getStatus()).isEqualTo(BattleStatus.EXPIRED);
		assertThat(battleRepository.findById(battleId).orElseThrow().getActiveRoomCode()).isNull();
		join(bob, roomCode).andExpect(status().isGone());   // 코드(room_code)는 남아 있어 만료를 계속 알려 준다
	}

	@Test
	void 입장자의_그_주차_노트가_0개면_주차를_알려_주며_거부한다() throws Exception {
		User carol = newUser("c");
		discipleRepository.saveAndFlush(Disciple.create(carol.getId(), courseId, "노트없음", null));
		String roomCode = createRoom(alice);

		join(carol, roomCode)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BATTLE_NOTES_REQUIRED"))
			.andExpect(jsonPath("$.details.courseId").value(courseId))
			.andExpect(jsonPath("$.details.unit.id").value(unitId))
			.andExpect(jsonPath("$.details.unit.title").value("3주차"));
	}

	@Test
	void 제자가_없는_학생은_방을_만들거나_입장할_수_없다() throws Exception {
		User dave = newUser("d");

		createRoomRequest(dave).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BATTLE_DISCIPLE_REQUIRED"));
		join(dave, createRoom(alice)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BATTLE_DISCIPLE_REQUIRED"));
	}

	@Test
	void 진행_중인_랭킹_방이_있으면_또_만들_수_없다() throws Exception {
		createRoom(alice);

		createRoomRequest(alice).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BATTLE_ALREADY_ACTIVE"));
	}

	@Test
	void 문항이_부족하면_409이고_재고_보충을_건다() throws Exception {
		when(questionBankService.readiness(anyLong(), eq(unitId), anyInt())).thenReturn(new BattleReadinessResponse(false, 1, 5));

		createRoomRequest(alice).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BATTLE_QUESTION_BANK_NOT_READY"));

		verify(questionBankService).requestRestock(unitId);
	}

	@Test
	void 잘못된_요청_값은_400이다() throws Exception {
		mockMvc.perform(post("/api/battles").header("Authorization", bearer(alice)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"courseId\": %d, \"unitId\": %d, \"questionCount\": 2}".formatted(courseId, unitId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		mockMvc.perform(patch("/api/battles/{id}/participants/me", 1).header("Authorization", bearer(alice))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
	}

	// --- 취소 ---

	@Test
	void 참가자_누구나_방을_취소할_수_있고_두_번_눌러도_성공이며_코드는_더_쓸_수_없다() throws Exception {
		String roomCode = createRoom(alice);
		join(bob, roomCode).andExpect(status().isOk());
		long battleId = battleIdOf(roomCode);

		cancel(bob, battleId).andExpect(status().isNoContent());   // 입장자도 방 전체를 취소한다
		cancel(alice, battleId).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/battles/{id}", battleId).header("Authorization", bearer(alice)))
			.andExpect(jsonPath("$.status").value("CANCELED"));
		join(newUser("c"), roomCode).andExpect(status().isNotFound());
		createRoomRequest(alice).andExpect(status().isCreated());   // 취소했으니 새 방을 만들 수 있다
	}

	@Test
	void 참가자가_아니면_취소할_수_없다() throws Exception {
		String roomCode = createRoom(alice);

		cancel(newUser("c"), battleIdOf(roomCode)).andExpect(status().isNotFound());
	}

	// --- 준비·시작 ---

	@Test
	void 둘이_준비하면_문항이_고정되고_RUNNING이_되며_준비_순간의_노트가_스냅샷된다() throws Exception {
		String roomCode = createRoom(alice);
		join(bob, roomCode).andExpect(status().isOk());
		long battleId = battleIdOf(roomCode);

		ready(alice, battleId, "{\"notesPublic\": true}").andExpect(status().isNoContent());
		ready(alice, battleId, null).andExpect(status().isNoContent());   // 멱등
		assertThat(battleRepository.findById(battleId).orElseThrow().getStatus()).isEqualTo(BattleStatus.LOBBY);

		// 준비한 뒤에 노트가 늘어도 스냅샷은 그대로다
		addNote(aliceDiscipleId, "스택에는 push 와 pop 이 있다.");
		ready(bob, battleId, null).andExpect(status().isNoContent());

		Battle battle = battleRepository.findById(battleId).orElseThrow();
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.RUNNING);
		assertThat(battle.getStartedAt()).isNotNull();
		assertThat(battle.getActiveRoomCode()).isNull();
		assertThat(battleQuestionRepository.findByBattleIdOrderBySeqAsc(battleId)).extracting(BattleQuestion::getQuestionId)
			.containsExactlyElementsOf(questionIds);

		BattleParticipant aliceParticipant = participantRepository.findByBattleIdAndUserId(battleId, alice.getId()).orElseThrow();
		assertThat(aliceParticipant.getNoteCountSnapshot()).isEqualTo(1);
		assertThat(aliceParticipant.isNotesPublic()).isTrue();
		assertThat(participantRepository.findByBattleIdAndUserId(battleId, bob.getId()).orElseThrow().isNotesPublic()).isFalse();
		verify(questionBankService).recordSeen(anyCollection(), eq(questionIds), eq(battleId));

		mockMvc.perform(get("/api/battles/{id}", battleId).header("Authorization", bearer(bob)))
			.andExpect(jsonPath("$.status").value("RUNNING"))
			.andExpect(jsonPath("$.participants[0].status").value("READY"))
			.andExpect(jsonPath("$.participants[1].status").value("READY"))
			.andExpect(jsonPath("$.participants[1].solveStatus").value("PENDING"));
		cancel(alice, battleId).andExpect(status().isConflict());   // 풀이 중에는 취소할 수 없다
	}

	@Test
	void 둘_다_안_본_문항이_모자라면_시작하지_않고_준비가_풀린다() throws Exception {
		when(questionBankService.pickForBattle(eq(unitId), anyCollection(), anyInt(), anyLong())).thenReturn(List.of());
		String roomCode = createRoom(alice);
		join(bob, roomCode).andExpect(status().isOk());
		long battleId = battleIdOf(roomCode);
		ready(alice, battleId, null).andExpect(status().isNoContent());

		ready(bob, battleId, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BATTLE_NOT_ENOUGH_QUESTIONS"));

		assertThat(battleRepository.findById(battleId).orElseThrow().getStatus()).isEqualTo(BattleStatus.LOBBY);
		assertThat(participantRepository.findByBattleIdOrderByIdAsc(battleId)).allSatisfy(participant -> {
			assertThat(participant.isReady()).isFalse();
			assertThat(participant.getNoteIdsJson()).isNull();
		});
		verify(questionBankService).requestRestock(unitId);
	}

	@Test
	void 상대가_오기_전에는_준비할_수_없다() throws Exception {
		String roomCode = createRoom(alice);

		ready(alice, battleIdOf(roomCode), null).andExpect(status().isConflict());
	}

	// --- 공개 동의 ---

	@Test
	void 내_노트_공개_동의를_바꿀_수_있다() throws Exception {
		String roomCode = createRoom(alice);
		long battleId = battleIdOf(roomCode);

		mockMvc.perform(patch("/api/battles/{id}/participants/me", battleId).header("Authorization", bearer(alice))
				.contentType(MediaType.APPLICATION_JSON).content("{\"notesPublic\": true}"))
			.andExpect(status().isNoContent());

		assertThat(participantRepository.findByBattleIdAndUserId(battleId, alice.getId()).orElseThrow().isNotesPublic()).isTrue();
	}

	// --- 내 배틀 기록 ---

	@Test
	void 내_배틀_기록은_내가_참가한_것만_조건대로_준다() throws Exception {
		String roomCode = createRoom(alice);
		join(bob, roomCode).andExpect(status().isOk());

		mockMvc.perform(get("/api/battles").header("Authorization", bearer(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].status").value("LOBBY"))
			.andExpect(jsonPath("$.items[0].opponent.nickname").value(alice.getNickname()))
			.andExpect(jsonPath("$.items[0].myScore").value(nullValue()));
		mockMvc.perform(get("/api/battles").param("mode", "PRACTICE").header("Authorization", bearer(bob)))
			.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/battles").param("status", "CANCELED").header("Authorization", bearer(bob)))
			.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/battles").param("courseId", String.valueOf(courseId)).header("Authorization", bearer(alice)))
			.andExpect(jsonPath("$.totalElements").value(1));
		mockMvc.perform(get("/api/battles").header("Authorization", bearer(newUser("c"))))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	// --- 제자 풀이(J-03)의 DB 단계 ---

	@Test
	void 풀이를_시작하고_끝내면_답이_채점_전으로_저장된다() throws Exception {
		long battleId = startedBattle();
		BattleParticipant aliceParticipant = participantRepository.findByBattleIdAndUserId(battleId, alice.getId()).orElseThrow();

		BattleSolveStore.SolveContext context = solveStore.begin(aliceParticipant.getId()).orElseThrow();
		assertThat(context.notes()).hasSize(1);
		assertThat(context.notes().get(0).content()).contains("스택");
		assertThat(context.questions()).extracting(DiscipleSolver.SolveQuestion::id).containsExactlyElementsOf(questionIds);
		assertThat(solveStore.begin(aliceParticipant.getId())).isEmpty();   // 두 번 시작되지 않는다

		long noteId = context.notes().get(0).id();
		solveStore.complete(aliceParticipant.getId(), List.of(
			new DiscipleSolver.SolvedAnswer(questionIds.get(0), "후입선출", false, List.of(noteId), false),
			new DiscipleSolver.SolvedAnswer(questionIds.get(1), "", true, List.of(), false),
			new DiscipleSolver.SolvedAnswer(questionIds.get(2), "", true, List.of(), true)), 1);

		List<BattleAnswer> answers = answerRepository.findByParticipantId(aliceParticipant.getId());
		assertThat(answers).hasSize(3);
		assertThat(answers).extracting(BattleAnswer::getVerdict).containsOnly(Verdict.PENDING);
		assertThat(answers).filteredOn(answer -> !answer.isUnknown()).singleElement()
			.satisfies(answer -> assertThat(answer.getAnswerText()).isEqualTo("후입선출"));
		assertThat(participantRepository.findById(aliceParticipant.getId()).orElseThrow().getSolveStatus()).isEqualTo(SolveStatus.SOLVED);
		assertThat(battleRepository.findById(battleId).orElseThrow().getStatus()).isEqualTo(BattleStatus.RUNNING);   // 상대가 아직
	}

	@Test
	void 풀이가_실패하면_ERROR가_되고_참가자_누구나_재시도하거나_포기할_수_있다() throws Exception {
		long battleId = startedBattle();
		BattleParticipant aliceParticipant = participantRepository.findByBattleIdAndUserId(battleId, alice.getId()).orElseThrow();
		solveStore.begin(aliceParticipant.getId()).orElseThrow();
		solveStore.fail(aliceParticipant.getId(), "AI_UNAVAILABLE", 3);

		mockMvc.perform(get("/api/battles/{id}", battleId).header("Authorization", bearer(bob)))
			.andExpect(jsonPath("$.status").value("ERROR"))
			.andExpect(jsonPath("$.participants[0].solveStatus").value("FAILED"));

		mockMvc.perform(post("/api/battles/{id}/solve/retry", battleId).header("Authorization", bearer(bob)))
			.andExpect(status().isAccepted());
		assertThat(battleRepository.findById(battleId).orElseThrow().getStatus()).isEqualTo(BattleStatus.RUNNING);
		assertThat(participantRepository.findById(aliceParticipant.getId()).orElseThrow().getSolveStatus()).isEqualTo(SolveStatus.PENDING);
		mockMvc.perform(post("/api/battles/{id}/solve/retry", battleId).header("Authorization", bearer(bob)))
			.andExpect(status().isConflict());   // ERROR 가 아니면 재시도할 수 없다

		solveStore.begin(aliceParticipant.getId()).orElseThrow();
		solveStore.fail(aliceParticipant.getId(), "AI_TIMEOUT", 3);
		cancel(bob, battleId).andExpect(status().isNoContent());   // ERROR 에서는 포기하고 나갈 수 있다
		assertThat(battleRepository.findById(battleId).orElseThrow().getStatus()).isEqualTo(BattleStatus.CANCELED);
	}

	// --- 연습 배틀(B-10) ---

	@Test
	void 끝난_랭킹_배틀의_틀린_답으로_연습_배틀을_시작하면_같은_문항이_RUNNING으로_나온다() throws Exception {
		long sourceId = finishedBattle(Verdict.CORRECT, Verdict.WRONG, Verdict.UNKNOWN);

		ResultActions started = mockMvc.perform(post("/api/battles/practice").header("Authorization", bearer(alice))
			.contentType(MediaType.APPLICATION_JSON).content("{\"sourceBattleId\": %d}".formatted(sourceId)));

		started.andExpect(status().isCreated())
			.andExpect(jsonPath("$.mode").value("PRACTICE"))
			.andExpect(jsonPath("$.status").value("RUNNING"))
			.andExpect(jsonPath("$.questionCount").value(2))
			.andExpect(jsonPath("$.skippedAnswerIds.length()").value(0));
		long practiceId = ((Number) JsonPath.read(started.andReturn().getResponse().getContentAsString(), "$.id")).longValue();

		List<BattleQuestion> questions = battleQuestionRepository.findByBattleIdOrderBySeqAsc(practiceId);
		assertThat(questions).extracting(BattleQuestion::getQuestionId).containsExactly(questionIds.get(1), questionIds.get(2));
		assertThat(questions).extracting(BattleQuestion::getSourceAnswerId).doesNotContainNull();
		mockMvc.perform(get("/api/battles/{id}", practiceId).header("Authorization", bearer(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("PRACTICE"))
			.andExpect(jsonPath("$.roomCode").value(nullValue()))
			.andExpect(jsonPath("$.participants.length()").value(1));

		// 이미 진행 중인 연습이 있으면 또 시작할 수 없다
		mockMvc.perform(post("/api/battles/practice").header("Authorization", bearer(alice)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"sourceBattleId\": %d}".formatted(sourceId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BATTLE_ALREADY_ACTIVE"));
		// 연습 기록은 mode=PRACTICE 로 모아 본다
		mockMvc.perform(get("/api/battles").param("mode", "PRACTICE").header("Authorization", bearer(alice)))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void 연습_배틀을_원본으로_삼거나_정답_보기를_누른_배틀을_원본으로_삼으면_거부된다() throws Exception {
		long sourceId = finishedBattle(Verdict.WRONG, Verdict.WRONG, Verdict.WRONG);
		Battle source = battleRepository.findById(sourceId).orElseThrow();
		ReflectionTestUtils.setField(source, "mode", com.aidisciple.domain.battle.entity.BattleMode.PRACTICE);
		battleRepository.saveAndFlush(source);
		mockMvc.perform(post("/api/battles/practice").header("Authorization", bearer(alice)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"sourceBattleId\": %d}".formatted(sourceId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));

		ReflectionTestUtils.setField(source, "mode", com.aidisciple.domain.battle.entity.BattleMode.RANKED);
		battleRepository.saveAndFlush(source);
		BattleParticipant mine = participantRepository.findByBattleIdAndUserId(sourceId, alice.getId()).orElseThrow();
		ReflectionTestUtils.setField(mine, "answersRevealedAt", Instant.now());
		participantRepository.saveAndFlush(mine);
		mockMvc.perform(post("/api/battles/practice").header("Authorization", bearer(alice)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"sourceBattleId\": %d}".formatted(sourceId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRACTICE_ANSWERS_REVEALED"));
	}

	@Test
	void 남의_배틀이나_맞힌_것만_있는_배틀로는_연습을_시작할_수_없다() throws Exception {
		long sourceId = finishedBattle(Verdict.CORRECT, Verdict.CORRECT, Verdict.CORRECT);

		mockMvc.perform(post("/api/battles/practice").header("Authorization", bearer(alice)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"sourceBattleId\": %d}".formatted(sourceId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRACTICE_NOTHING_TO_REVIEW"));
		mockMvc.perform(post("/api/battles/practice").header("Authorization", bearer(bob)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"sourceBattleId\": %d}".formatted(sourceId)))
			.andExpect(status().isNotFound());
	}

	// --- 도우미 ---

	private ResultActions createRoomRequest(User user) throws Exception {
		return mockMvc.perform(post("/api/battles").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"courseId\": %d, \"unitId\": %d, \"questionCount\": 3}".formatted(courseId, unitId)));
	}

	private String createRoom(User user) throws Exception {
		ResultActions created = createRoomRequest(user).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("WAITING"))
			.andExpect(jsonPath("$.roomCode").isString());
		return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.roomCode");
	}

	private ResultActions join(User user, String roomCode) throws Exception {
		return mockMvc.perform(post("/api/battles/join").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"roomCode\": \"%s\"}".formatted(roomCode)));
	}

	private ResultActions ready(User user, long battleId, String body) throws Exception {
		var request = post("/api/battles/{id}/ready", battleId).header("Authorization", bearer(user));
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private ResultActions cancel(User user, long battleId) throws Exception {
		return mockMvc.perform(post("/api/battles/{id}/cancel", battleId).header("Authorization", bearer(user)));
	}

	private long battleIdOf(String roomCode) {
		return battleRepository.findByActiveRoomCode(roomCode.toUpperCase()).orElseThrow().getId();
	}

	/** 둘이 입장·준비해 RUNNING 이 된 배틀 */
	private long startedBattle() throws Exception {
		String roomCode = createRoom(alice);
		join(bob, roomCode).andExpect(status().isOk());
		long battleId = battleIdOf(roomCode);
		ready(alice, battleId, null).andExpect(status().isNoContent());
		ready(bob, battleId, null).andExpect(status().isNoContent());
		return battleId;
	}

	/** 앨리스의 답이 문항 순서대로 주어진 판정인, 끝난 랭킹 배틀(채점은 WP9 가 하므로 판정을 직접 넣는다) */
	private long finishedBattle(Verdict first, Verdict second, Verdict third) {
		Battle battle = Battle.openRoom(courseId, unitId, 3, alice.getId(), "ZZZZZ2", Instant.now());
		ReflectionTestUtils.setField(battle, "status", BattleStatus.FINISHED);
		ReflectionTestUtils.setField(battle, "activeRoomCode", null);
		battle = battleRepository.saveAndFlush(battle);
		BattleParticipant mine = participantRepository.saveAndFlush(
			BattleParticipant.joined(battle.getId(), alice.getId(), aliceDiscipleId, true, Instant.now()));
		List<Verdict> verdicts = List.of(first, second, third);
		for (int i = 0; i < 3; i++) {
			BattleQuestion question = battleQuestionRepository.saveAndFlush(
				BattleQuestion.of(battle.getId(), questionIds.get(i), i + 1, null));
			BattleAnswer answer = BattleAnswer.solved(mine.getId(), question.getId(), "답", false, "[]");
			ReflectionTestUtils.setField(answer, "verdict", verdicts.get(i));
			answerRepository.saveAndFlush(answer);
		}
		return battle.getId();
	}

	private User newUser(String tag) {
		return userRepository.saveAndFlush(User.create(schoolId, null, "sub-" + tag + suffix,
			tag + suffix + "@mju.ac.kr", tag + suffix, UserRole.STUDENT, Instant.now()));
	}

	private void addNote(long discipleId, String content) {
		ChatMessage message = chatMessageRepository.saveAndFlush(
			ChatMessage.student(discipleId, unitId, 1, content, UUID.randomUUID().toString()));
		noteEntryRepository.saveAndFlush(NoteEntry.create(discipleId, unitId, content, content, message.getId(), null, 1, "{}"));
	}

	private long newQuestion(String stem) {
		return questionRepository.saveAndFlush(Question.fromAi(courseId, unitId, stem + suffix, GradingMode.ANSWER_LIST,
			"[\"후입선출\"]", null, null, "후입선출", "근거 문장", QuestionStatus.UNJUDGED, null)).getId();
	}

	private String bearer(User user) {
		return "Bearer " + jwtProvider.issueAccessToken(user.getId(), user.getRole());
	}

}
