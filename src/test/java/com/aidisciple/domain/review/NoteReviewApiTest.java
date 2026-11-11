package com.aidisciple.domain.review;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.ai.LlmClient;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.Disciple;
import com.aidisciple.domain.course.entity.Unit;
import com.aidisciple.domain.course.repository.CourseRepository;
import com.aidisciple.domain.course.repository.DiscipleRepository;
import com.aidisciple.domain.course.repository.UnitRepository;
import com.aidisciple.domain.teaching.entity.ChatMessage;
import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.repository.ChatMessageRepository;
import com.aidisciple.domain.teaching.repository.NoteEntryRepository;
import com.aidisciple.global.security.UserRole;

/** 노트 교환 리뷰 R-06·R-07·R-08 을 DB 까지 이어서 확인한다. 각 테스트는 트랜잭션으로 감싸 끝나면 되돌린다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoteReviewApiTest {

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
	BattleRepository battleRepository;

	@Autowired
	BattleParticipantRepository participantRepository;

	@MockitoBean
	LlmClient llmClient;

	private String suffix;
	private long schoolId;
	private User alice;
	private User bob;
	private User stranger;
	private long courseId;
	private long unitId;
	private long bobDiscipleId;
	private int turnSeq = 0;
	/** 밥이 준비할 때 스냅샷한 노트 2개와, 그 뒤에 새로 쓴 노트 1개 */
	private long bobNote1;
	private long bobNote2;
	private long bobLaterNote;

	@BeforeEach
	void 두_학생과_밥의_노트를_만든다() {
		suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		schoolId = schoolRepository.findByCode("MJU").orElseThrow().getId();
		alice = newUser("a");
		bob = newUser("b");
		stranger = newUser("s");
		String courseName = "자료구조" + suffix;
		courseId = courseRepository.saveAndFlush(Course.create(schoolId, null, courseName, courseName, "2026-2", alice.getId())).getId();
		unitId = unitRepository.saveAndFlush(Unit.create(courseId, "3주차", "3주차", alice.getId())).getId();
		bobDiscipleId = discipleRepository.saveAndFlush(Disciple.create(bob.getId(), courseId, "토리", null)).getId();
		bobNote1 = addNote(bobDiscipleId, "스택은 나중에 넣은 것이 먼저 나온다.");
		bobNote2 = addNote(bobDiscipleId, "큐는 먼저 넣은 것이 먼저 나온다.");
		bobLaterNote = addNote(bobDiscipleId, "준비한 뒤에 쓴 노트");
	}

	@Test
	void 둘_다_공개하면_상대가_준비할_때_스냅샷한_노트만_보인다() throws Exception {
		long battleId = finishedBattle(true, true);

		opponentNotes(alice, battleId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.opponent.userId").value(bob.getId()))
			.andExpect(jsonPath("$.opponent.nickname").value("b" + suffix))
			.andExpect(jsonPath("$.notes.length()").value(2))
			.andExpect(jsonPath("$.notes[0].id").value(bobNote1))
			.andExpect(jsonPath("$.notes[1].content").value("큐는 먼저 넣은 것이 먼저 나온다."));
	}

	@Test
	void 한쪽이라도_비공개면_노트도_리뷰_쓰기도_403이고_리뷰_목록은_비어_있다() throws Exception {
		long battleId = finishedBattle(true, false);

		opponentNotes(alice, battleId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("REVIEW_NOTES_NOT_PUBLIC"));
		writeReview(alice, battleId, null)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("REVIEW_NOTES_NOT_PUBLIC"));
		listReviews(alice, battleId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.written").value(nullValue()))
			.andExpect(jsonPath("$.received").value(nullValue()));
	}

	@Test
	void 리뷰는_배틀당_한_번이고_상대에게는_받은_리뷰로_보인다() throws Exception {
		long battleId = finishedBattle(true, true);

		writeReview(alice, battleId, bobNote1)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.reviewer.userId").value(alice.getId()))
			.andExpect(jsonPath("$.target.userId").value(bob.getId()))
			.andExpect(jsonPath("$.evidenceNote.id").value(bobNote1));

		writeReview(alice, battleId, null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REVIEW_ALREADY_SUBMITTED"));

		listReviews(bob, battleId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.written").value(nullValue()))
			.andExpect(jsonPath("$.received.goodPoints").value("정의가 정확해요"))
			.andExpect(jsonPath("$.received.evidenceNote.content").value("스택은 나중에 넣은 것이 먼저 나온다."));
		listReviews(alice, battleId)
			.andExpect(jsonPath("$.written.suggestions").value("예시를 하나 더 넣어 보세요"))
			.andExpect(jsonPath("$.received").value(nullValue()));
	}

	@Test
	void 근거_노트가_상대의_스냅샷에_없으면_400() throws Exception {
		long battleId = finishedBattle(true, true);

		writeReview(alice, battleId, bobLaterNote)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 참가하지_않은_사람과_끝나지_않은_배틀은_막힌다() throws Exception {
		long battleId = finishedBattle(true, true);
		opponentNotes(stranger, battleId).andExpect(status().isNotFound());
		listReviews(stranger, battleId).andExpect(status().isNotFound());

		Battle battle = battleRepository.findById(battleId).orElseThrow();
		ReflectionTestUtils.setField(battle, "status", BattleStatus.RUNNING);
		battleRepository.saveAndFlush(battle);
		opponentNotes(alice, battleId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BATTLE_NOT_FINISHED"));
	}

	// --- 준비 도우미 ---

	/** 앨리스·밥이 참가한 끝난 랭킹 배틀. 밥은 노트 2개를 스냅샷했다. 공개 동의는 인자대로. */
	private long finishedBattle(boolean alicePublic, boolean bobPublic) {
		Battle battle = Battle.openRoom(courseId, unitId, 3, alice.getId(), "ZZZZZ6", Instant.now());
		ReflectionTestUtils.setField(battle, "status", BattleStatus.FINISHED);
		ReflectionTestUtils.setField(battle, "activeRoomCode", null);
		long battleId = battleRepository.saveAndFlush(battle).getId();
		long aliceDiscipleId = discipleRepository.saveAndFlush(Disciple.create(alice.getId(), courseId, "마리", null)).getId();

		BattleParticipant aliceParticipant = BattleParticipant.joined(battleId, alice.getId(), aliceDiscipleId, true, Instant.now());
		aliceParticipant.markReady("[]", 0, alicePublic, Instant.now());
		participantRepository.saveAndFlush(aliceParticipant);
		BattleParticipant bobParticipant = BattleParticipant.joined(battleId, bob.getId(), bobDiscipleId, false, Instant.now());
		bobParticipant.markReady("[" + bobNote1 + "," + bobNote2 + "]", 2, bobPublic, Instant.now());
		participantRepository.saveAndFlush(bobParticipant);
		return battleId;
	}

	private ResultActions opponentNotes(User user, long battleId) throws Exception {
		return mockMvc.perform(get("/api/battles/{id}/opponent-notes", battleId).header("Authorization", bearer(user)));
	}

	private ResultActions writeReview(User user, long battleId, Long evidenceNoteId) throws Exception {
		String evidence = evidenceNoteId == null ? "null" : String.valueOf(evidenceNoteId);
		return mockMvc.perform(post("/api/battles/{id}/note-reviews", battleId).header("Authorization", bearer(user))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"goodPoints": "정의가 정확해요", "suggestions": "예시를 하나 더 넣어 보세요", "evidenceNoteEntryId": %s}
				""".formatted(evidence)));
	}

	private ResultActions listReviews(User user, long battleId) throws Exception {
		return mockMvc.perform(get("/api/battles/{id}/note-reviews", battleId).header("Authorization", bearer(user)));
	}

	private User newUser(String tag) {
		return userRepository.saveAndFlush(User.create(schoolId, null, "sub-" + tag + suffix,
			tag + suffix + "@mju.ac.kr", tag + suffix, UserRole.STUDENT, Instant.now()));
	}

	private long addNote(long discipleId, String content) {
		ChatMessage message = chatMessageRepository.saveAndFlush(
			ChatMessage.student(discipleId, unitId, ++turnSeq, content, UUID.randomUUID().toString()));
		return noteEntryRepository.saveAndFlush(
			NoteEntry.create(discipleId, unitId, content, content, message.getId(), null, 1, "{}")).getId();
	}

	private String bearer(User user) {
		return "Bearer " + jwtProvider.issueAccessToken(user.getId(), user.getRole());
	}

}
