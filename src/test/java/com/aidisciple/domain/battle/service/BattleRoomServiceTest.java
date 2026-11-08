package com.aidisciple.domain.battle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.battle.config.BattleProperties;
import com.aidisciple.domain.battle.dto.BattleResponse;
import com.aidisciple.domain.battle.dto.CreateBattleRequest;
import com.aidisciple.domain.battle.dto.CreateBattleResponse;
import com.aidisciple.domain.battle.dto.JoinBattleRequest;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.course.dto.DiscipleRef;
import com.aidisciple.domain.course.dto.UnitRef;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.service.CourseService;
import com.aidisciple.domain.course.service.DiscipleService;
import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.domain.question.dto.BattleReadinessResponse;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.domain.teaching.dto.NoteRef;
import com.aidisciple.domain.teaching.service.NoteLookupService;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.ratelimit.RateLimitProperties;
import com.aidisciple.global.ratelimit.RateLimiter;

class BattleRoomServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
	private static final long ME = 10L;
	private static final long FRIEND = 11L;
	private static final long COURSE_ID = 1L;
	private static final long UNIT_ID = 2L;
	private static final long BATTLE_ID = 50L;

	private final BattleRepository battleRepository = mock(BattleRepository.class);
	private final BattleParticipantRepository participantRepository = mock(BattleParticipantRepository.class);
	private final BattleQueryService queryService = mock(BattleQueryService.class);
	private final UserLookupService userLookupService = mock(UserLookupService.class);
	private final CourseService courseService = mock(CourseService.class);
	private final UnitService unitService = mock(UnitService.class);
	private final DiscipleService discipleService = mock(DiscipleService.class);
	private final NoteLookupService noteLookupService = mock(NoteLookupService.class);
	private final QuestionBankService questionBankService = mock(QuestionBankService.class);
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
	private final RateLimitProperties rateLimitProperties = new RateLimitProperties(
		new RateLimitProperties.Rule(10, Duration.ofMinutes(1)), new RateLimitProperties.Rule(12, Duration.ofMinutes(1)),
		new RateLimitProperties.Rule(5, Duration.ofMinutes(10)), new RateLimitProperties.Rule(10, Duration.ofHours(1)));

	private final BattleRoomService service = new BattleRoomService(battleRepository, participantRepository, queryService,
		userLookupService, courseService, unitService, discipleService, noteLookupService, questionBankService,
		new RateLimiter(clock), rateLimitProperties, BattleProperties.defaults(), clock);

	private final UserRef me = new UserRef(ME, 1L, null);
	private final UserRef friend = new UserRef(FRIEND, 1L, null);
	private final UnitRef unit = new UnitRef(UNIT_ID, COURSE_ID, "3주차");
	private final BattleResponse response = new BattleResponse(BATTLE_ID, BattleStatus.LOBBY, BattleMode.RANKED, null, null,
		5, "K7M4QX", null, List.of(), null);

	private Course course;

	@BeforeEach
	void 기본_상태() {
		course = Course.create(1L, null, "자료구조", "자료구조", "2026-2", ME);
		ReflectionTestUtils.setField(course, "id", COURSE_ID);
		when(userLookupService.lockActive(ME)).thenReturn(me);
		when(userLookupService.lockActive(FRIEND)).thenReturn(friend);
		when(courseService.requireVisible(COURSE_ID, me)).thenReturn(course);
		when(courseService.requireVisible(COURSE_ID, friend)).thenReturn(course);
		when(unitService.requireInCourse(COURSE_ID, UNIT_ID)).thenReturn(unit);
		when(unitService.findRefs(List.of(UNIT_ID))).thenReturn(Map.of(UNIT_ID, unit));
		when(discipleService.findByUserAndCourse(ME, COURSE_ID)).thenReturn(Optional.of(new DiscipleRef(100L, ME, COURSE_ID)));
		when(discipleService.findByUserAndCourse(FRIEND, COURSE_ID)).thenReturn(Optional.of(new DiscipleRef(101L, FRIEND, COURSE_ID)));
		when(noteLookupService.activeNotes(anyLong(), eq(UNIT_ID))).thenReturn(List.of(new NoteRef(7L, "노트")));
		when(questionBankService.readiness(anyLong(), eq(UNIT_ID), anyInt())).thenReturn(new BattleReadinessResponse(true, 12, 5));
		when(battleRepository.saveAndFlush(any(Battle.class))).thenAnswer(invocation -> {
			Battle saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", BATTLE_ID);
			return saved;
		});
		when(participantRepository.save(any(BattleParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(queryService.toResponse(any(Battle.class), anyList(), anyLong())).thenReturn(response);
	}

	private static BusinessException thrownBy(Runnable action) {
		Throwable thrown = catchThrowable(action::run);
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return (BusinessException) thrown;
	}

	private static ErrorCode codeOf(Runnable action) {
		return thrownBy(action).getErrorCode();
	}

	private Battle room(BattleStatus status, long creatorId) {
		Battle battle = Battle.openRoom(COURSE_ID, UNIT_ID, 5, creatorId, "K7M4QX", NOW.plus(Duration.ofMinutes(20)));
		ReflectionTestUtils.setField(battle, "id", BATTLE_ID);
		ReflectionTestUtils.setField(battle, "status", status);
		when(battleRepository.findByActiveRoomCode("K7M4QX")).thenReturn(Optional.of(battle));
		when(battleRepository.findLockedById(BATTLE_ID)).thenReturn(Optional.of(battle));
		return battle;
	}

	private void creatorIsInRoom() {
		BattleParticipant creator = BattleParticipant.joined(BATTLE_ID, ME, 100L, true, NOW);
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(creator));
	}

	// --- B-01 ---

	@Test
	void 방을_만들면_WAITING_방과_방장_참가자가_생긴다() {
		CreateBattleResponse created = service.create(ME, new CreateBattleRequest(COURSE_ID, UNIT_ID, 5));

		assertThat(created.id()).isEqualTo(BATTLE_ID);
		assertThat(created.status()).isEqualTo(BattleStatus.WAITING);
		assertThat(created.roomCode()).matches("[A-HJKMNP-Z2-9]{6}");
		assertThat(created.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));

		ArgumentCaptor<Battle> battle = ArgumentCaptor.forClass(Battle.class);
		verify(battleRepository).saveAndFlush(battle.capture());
		assertThat(battle.getValue().getMode()).isEqualTo(BattleMode.RANKED);
		assertThat(battle.getValue().getQuestionCount()).isEqualTo(5);
		assertThat(battle.getValue().getActiveRoomCode()).isEqualTo(created.roomCode());

		ArgumentCaptor<BattleParticipant> participant = ArgumentCaptor.forClass(BattleParticipant.class);
		verify(participantRepository).save(participant.capture());
		assertThat(participant.getValue().isCreator()).isTrue();
		assertThat(participant.getValue().getUserId()).isEqualTo(ME);
		assertThat(participant.getValue().getDiscipleId()).isEqualTo(100L);
	}

	@Test
	void 문항_수를_생략하면_5개로_만든다() {
		service.create(ME, new CreateBattleRequest(COURSE_ID, UNIT_ID, null));

		ArgumentCaptor<Battle> battle = ArgumentCaptor.forClass(Battle.class);
		verify(battleRepository).saveAndFlush(battle.capture());
		assertThat(battle.getValue().getQuestionCount()).isEqualTo(5);
		verify(questionBankService).readiness(ME, UNIT_ID, 5);
	}

	@Test
	void 제자가_없으면_방을_만들_수_없다() {
		when(discipleService.findByUserAndCourse(ME, COURSE_ID)).thenReturn(Optional.empty());

		assertThat(codeOf(() -> service.create(ME, new CreateBattleRequest(COURSE_ID, UNIT_ID, 5))))
			.isEqualTo(ErrorCode.BATTLE_DISCIPLE_REQUIRED);
		verify(battleRepository, never()).saveAndFlush(any());
	}

	@Test
	void 진행_중인_랭킹_배틀이_있으면_방을_만들_수_없다() {
		when(battleRepository.hasInProgress(ME, BattleMode.RANKED, NOW)).thenReturn(true);

		assertThat(codeOf(() -> service.create(ME, new CreateBattleRequest(COURSE_ID, UNIT_ID, 5))))
			.isEqualTo(ErrorCode.BATTLE_ALREADY_ACTIVE);
		verify(battleRepository, never()).saveAndFlush(any());
	}

	@Test
	void 문항이_부족하면_부족을_알리고_재고_보충을_건다() {
		when(questionBankService.readiness(ME, UNIT_ID, 5)).thenReturn(new BattleReadinessResponse(false, 2, 5));

		assertThat(codeOf(() -> service.create(ME, new CreateBattleRequest(COURSE_ID, UNIT_ID, 5))))
			.isEqualTo(ErrorCode.BATTLE_QUESTION_BANK_NOT_READY);
		verify(questionBankService).requestRestock(UNIT_ID);
		verify(battleRepository, never()).saveAndFlush(any());
	}

	@Test
	void 노트가_없으면_주차와_과목을_알려_주며_거부한다() {
		when(noteLookupService.activeNotes(100L, UNIT_ID)).thenReturn(List.of());

		BusinessException e = thrownBy(() -> service.create(ME, new CreateBattleRequest(COURSE_ID, UNIT_ID, 5)));

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BATTLE_NOTES_REQUIRED);
		assertThat(e.getDetails()).containsEntry("courseId", COURSE_ID);
		assertThat(e.getDetails().get("unit")).isEqualTo(Map.of("id", UNIT_ID, "title", "3주차"));
		verify(battleRepository, never()).saveAndFlush(any());
	}

	@Test
	void 볼_수_없는_과목이거나_다른_과목의_주차면_방을_만들_수_없다() {
		when(courseService.requireVisible(9L, me)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));
		when(unitService.requireInCourse(COURSE_ID, 99L)).thenThrow(new BusinessException(ErrorCode.UNIT_NOT_IN_COURSE));

		assertThat(codeOf(() -> service.create(ME, new CreateBattleRequest(9L, UNIT_ID, 5)))).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(codeOf(() -> service.create(ME, new CreateBattleRequest(COURSE_ID, 99L, 5))))
			.isEqualTo(ErrorCode.UNIT_NOT_IN_COURSE);
	}

	@Test
	void 방_만들기는_10분_5회까지만_된다() {
		for (int i = 0; i < 5; i++) {
			service.create(ME, new CreateBattleRequest(COURSE_ID, UNIT_ID, 5));
		}

		assertThat(codeOf(() -> service.create(ME, new CreateBattleRequest(COURSE_ID, UNIT_ID, 5)))).isEqualTo(ErrorCode.RATE_LIMITED);
	}

	// --- B-02 ---

	@Test
	void 코드로_입장하면_LOBBY가_되고_입장자가_참가자로_저장된다() {
		Battle battle = room(BattleStatus.WAITING, ME);
		creatorIsInRoom();

		BattleResponse joined = service.join(FRIEND, new JoinBattleRequest(" k7m 4qx "));

		assertThat(joined).isSameAs(response);
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.LOBBY);
		ArgumentCaptor<BattleParticipant> participant = ArgumentCaptor.forClass(BattleParticipant.class);
		verify(participantRepository).save(participant.capture());
		assertThat(participant.getValue().getUserId()).isEqualTo(FRIEND);
		assertThat(participant.getValue().isCreator()).isFalse();
		assertThat(participant.getValue().getDiscipleId()).isEqualTo(101L);
		ArgumentCaptor<List<BattleParticipant>> everyone = ArgumentCaptor.captor();
		verify(queryService).toResponse(eq(battle), everyone.capture(), eq(FRIEND));
		assertThat(everyone.getValue()).hasSize(2);
	}

	@Test
	void 형식이_틀리거나_없는_코드는_BATTLE_ROOM_NOT_FOUND다() {
		when(battleRepository.findByActiveRoomCode("ZZZZZZ")).thenReturn(Optional.empty());
		when(battleRepository.findFirstByRoomCodeOrderByIdDesc("ZZZZZZ")).thenReturn(Optional.empty());

		assertThat(codeOf(() -> service.join(FRIEND, new JoinBattleRequest("abc")))).isEqualTo(ErrorCode.BATTLE_ROOM_NOT_FOUND);
		assertThat(codeOf(() -> service.join(FRIEND, new JoinBattleRequest("ZZZZZZ")))).isEqualTo(ErrorCode.BATTLE_ROOM_NOT_FOUND);
	}

	@Test
	void 만료된_방의_코드는_BATTLE_ROOM_EXPIRED다() {
		Battle expired = room(BattleStatus.EXPIRED, ME);
		when(battleRepository.findByActiveRoomCode("K7M4QX")).thenReturn(Optional.empty());
		when(battleRepository.findFirstByRoomCodeOrderByIdDesc("K7M4QX")).thenReturn(Optional.of(expired));

		assertThat(codeOf(() -> service.join(FRIEND, new JoinBattleRequest("K7M4QX")))).isEqualTo(ErrorCode.BATTLE_ROOM_EXPIRED);
	}

	@Test
	void 취소되거나_끝난_방의_코드는_없는_방이다() {
		Battle canceled = room(BattleStatus.CANCELED, ME);
		when(battleRepository.findByActiveRoomCode("K7M4QX")).thenReturn(Optional.empty());
		when(battleRepository.findFirstByRoomCodeOrderByIdDesc("K7M4QX")).thenReturn(Optional.of(canceled));

		assertThat(codeOf(() -> service.join(FRIEND, new JoinBattleRequest("K7M4QX")))).isEqualTo(ErrorCode.BATTLE_ROOM_NOT_FOUND);
	}

	@Test
	void 입장_시간이_지난_방은_만료_작업이_돌기_전이어도_만료다() {
		Battle overdue = room(BattleStatus.WAITING, ME);
		ReflectionTestUtils.setField(overdue, "expiresAt", NOW.minusSeconds(1));

		assertThat(codeOf(() -> service.join(FRIEND, new JoinBattleRequest("K7M4QX")))).isEqualTo(ErrorCode.BATTLE_ROOM_EXPIRED);
		assertThat(overdue.getStatus()).isEqualTo(BattleStatus.WAITING);   // 읽기 경로에서는 상태를 바꾸지 않는다(J-04 몫)
	}

	@Test
	void 방장이_자기_방에_입장하려_하면_BATTLE_SELF_JOIN이다() {
		room(BattleStatus.WAITING, ME);
		creatorIsInRoom();

		assertThat(codeOf(() -> service.join(ME, new JoinBattleRequest("K7M4QX")))).isEqualTo(ErrorCode.BATTLE_SELF_JOIN);
	}

	@Test
	void 이미_들어온_입장자가_다시_불러도_같은_응답이고_참가자를_또_만들지_않는다() {
		room(BattleStatus.LOBBY, ME);
		BattleParticipant creator = BattleParticipant.joined(BATTLE_ID, ME, 100L, true, NOW);
		BattleParticipant joiner = BattleParticipant.joined(BATTLE_ID, FRIEND, 101L, false, NOW);
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(creator, joiner));

		BattleResponse again = service.join(FRIEND, new JoinBattleRequest("K7M4QX"));

		assertThat(again).isSameAs(response);
		verify(participantRepository, never()).save(any());
	}

	@Test
	void 이미_두_명이_있는_방에는_세_번째가_들어올_수_없다() {
		room(BattleStatus.LOBBY, ME);
		BattleParticipant creator = BattleParticipant.joined(BATTLE_ID, ME, 100L, true, NOW);
		BattleParticipant joiner = BattleParticipant.joined(BATTLE_ID, 12L, 102L, false, NOW);
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(creator, joiner));
		when(userLookupService.lockActive(13L)).thenReturn(new UserRef(13L, 1L, null));
		when(courseService.requireVisible(eq(COURSE_ID), any(UserRef.class))).thenReturn(course);

		assertThat(codeOf(() -> service.join(13L, new JoinBattleRequest("K7M4QX")))).isEqualTo(ErrorCode.BATTLE_ROOM_FULL);
	}

	@Test
	void 입장자에게_제자가_없으면_BATTLE_DISCIPLE_REQUIRED다() {
		room(BattleStatus.WAITING, ME);
		creatorIsInRoom();
		when(discipleService.findByUserAndCourse(FRIEND, COURSE_ID)).thenReturn(Optional.empty());

		assertThat(codeOf(() -> service.join(FRIEND, new JoinBattleRequest("K7M4QX")))).isEqualTo(ErrorCode.BATTLE_DISCIPLE_REQUIRED);
		verify(participantRepository, never()).save(any());
	}

	@Test
	void 입장자의_노트가_없으면_주차를_알려_주며_거부하고_방은_그대로다() {
		Battle battle = room(BattleStatus.WAITING, ME);
		creatorIsInRoom();
		when(noteLookupService.activeNotes(101L, UNIT_ID)).thenReturn(List.of());

		BusinessException e = thrownBy(() -> service.join(FRIEND, new JoinBattleRequest("K7M4QX")));

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BATTLE_NOTES_REQUIRED);
		assertThat(e.getDetails()).containsEntry("courseId", COURSE_ID);
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.WAITING);
		verify(participantRepository, never()).save(any());
	}

	@Test
	void 입장자에게_진행_중인_배틀이_있으면_입장할_수_없다() {
		room(BattleStatus.WAITING, ME);
		creatorIsInRoom();
		when(battleRepository.hasInProgress(FRIEND, BattleMode.RANKED, NOW)).thenReturn(true);

		assertThat(codeOf(() -> service.join(FRIEND, new JoinBattleRequest("K7M4QX")))).isEqualTo(ErrorCode.BATTLE_ALREADY_ACTIVE);
	}

	@Test
	void 다른_학교의_방은_없는_방처럼_답한다() {
		room(BattleStatus.WAITING, ME);
		creatorIsInRoom();
		when(courseService.requireVisible(COURSE_ID, friend)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

		assertThat(codeOf(() -> service.join(FRIEND, new JoinBattleRequest("K7M4QX")))).isEqualTo(ErrorCode.NOT_FOUND);
	}

	// --- B-05 ---

	@Test
	void 참가자는_누구나_방을_취소할_수_있다() {
		Battle lobby = room(BattleStatus.LOBBY, ME);
		when(participantRepository.findByBattleIdAndUserId(BATTLE_ID, FRIEND))
			.thenReturn(Optional.of(BattleParticipant.joined(BATTLE_ID, FRIEND, 101L, false, NOW)));

		service.cancel(FRIEND, BATTLE_ID);

		assertThat(lobby.getStatus()).isEqualTo(BattleStatus.CANCELED);
		assertThat(lobby.getActiveRoomCode()).isNull();
	}

	@Test
	void 참가자가_아니면_취소할_수_없고_없는_방과_같은_NOT_FOUND다() {
		room(BattleStatus.WAITING, ME);
		when(participantRepository.findByBattleIdAndUserId(BATTLE_ID, FRIEND)).thenReturn(Optional.empty());

		assertThat(codeOf(() -> service.cancel(FRIEND, BATTLE_ID))).isEqualTo(ErrorCode.NOT_FOUND);
		when(battleRepository.findLockedById(404L)).thenReturn(Optional.empty());
		assertThat(codeOf(() -> service.cancel(ME, 404L))).isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void 이미_취소된_방을_다시_취소해도_성공이다() {
		Battle canceled = room(BattleStatus.CANCELED, ME);
		when(participantRepository.findByBattleIdAndUserId(BATTLE_ID, ME))
			.thenReturn(Optional.of(BattleParticipant.joined(BATTLE_ID, ME, 100L, true, NOW)));

		service.cancel(ME, BATTLE_ID);

		assertThat(canceled.getStatus()).isEqualTo(BattleStatus.CANCELED);
	}

	@Test
	void 풀이_중이거나_끝난_배틀은_취소할_수_없다() {
		room(BattleStatus.RUNNING, ME);
		when(participantRepository.findByBattleIdAndUserId(BATTLE_ID, ME))
			.thenReturn(Optional.of(BattleParticipant.joined(BATTLE_ID, ME, 100L, true, NOW)));

		assertThat(codeOf(() -> service.cancel(ME, BATTLE_ID))).isEqualTo(ErrorCode.CONFLICT);
	}

	@Test
	void ERROR_배틀은_포기하고_나갈_수_있다() {
		Battle error = room(BattleStatus.ERROR, ME);
		when(participantRepository.findByBattleIdAndUserId(BATTLE_ID, FRIEND))
			.thenReturn(Optional.of(BattleParticipant.joined(BATTLE_ID, FRIEND, 101L, false, NOW)));

		service.cancel(FRIEND, BATTLE_ID);

		assertThat(error.getStatus()).isEqualTo(BattleStatus.CANCELED);
	}

	// --- B-08 ---

	@Test
	void 내_노트_공개_동의를_바꾼다() {
		BattleParticipant mine = BattleParticipant.joined(BATTLE_ID, ME, 100L, true, NOW);
		when(participantRepository.findByBattleIdAndUserId(BATTLE_ID, ME)).thenReturn(Optional.of(mine));

		service.changeNotesPublic(ME, BATTLE_ID, true);
		assertThat(mine.isNotesPublic()).isTrue();

		service.changeNotesPublic(ME, BATTLE_ID, false);
		assertThat(mine.isNotesPublic()).isFalse();
	}

	@Test
	void 참가자가_아니면_공개_동의를_바꿀_수_없다() {
		when(participantRepository.findByBattleIdAndUserId(BATTLE_ID, FRIEND)).thenReturn(Optional.empty());

		assertThat(codeOf(() -> service.changeNotesPublic(FRIEND, BATTLE_ID, true))).isEqualTo(ErrorCode.NOT_FOUND);
	}

}
