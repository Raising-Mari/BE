package com.aidisciple.domain.battle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.battle.dto.BattleResponse;
import com.aidisciple.domain.battle.dto.BattleSummaryResponse;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleResult;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.course.dto.UnitRef;
import com.aidisciple.domain.course.service.CourseService;
import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class BattleQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
	private static final long ME = 10L;
	private static final long FRIEND = 11L;
	private static final long BATTLE_ID = 50L;

	private final BattleRepository battleRepository = mock(BattleRepository.class);
	private final BattleParticipantRepository participantRepository = mock(BattleParticipantRepository.class);
	private final UserLookupService userLookupService = mock(UserLookupService.class);
	private final CourseService courseService = mock(CourseService.class);
	private final UnitService unitService = mock(UnitService.class);
	private final BattleQueryService service = new BattleQueryService(battleRepository, participantRepository,
		userLookupService, courseService, unitService, Clock.fixed(NOW, ZoneOffset.UTC));

	private BattleParticipant mine;
	private BattleParticipant friends;

	@BeforeEach
	void 기본_상태() {
		mine = BattleParticipant.joined(BATTLE_ID, ME, 100L, true, NOW);
		friends = BattleParticipant.joined(BATTLE_ID, FRIEND, 101L, false, NOW);
		when(userLookupService.nicknames(anyCollection())).thenReturn(Map.of(ME, "나", FRIEND, "친구"));
		when(courseService.findNames(anyCollection())).thenReturn(Map.of(1L, "자료구조"));
		when(unitService.findRefs(anyCollection())).thenReturn(Map.of(2L, new UnitRef(2L, 1L, "3주차")));
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(mine, friends));
	}

	private Battle battle(BattleStatus status) {
		Battle battle = Battle.openRoom(1L, 2L, 5, ME, "K7M4QX", NOW.plus(Duration.ofMinutes(10)));
		ReflectionTestUtils.setField(battle, "id", BATTLE_ID);
		ReflectionTestUtils.setField(battle, "status", status);
		ReflectionTestUtils.setField(battle, "createdAt", NOW);
		when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
		return battle;
	}

	@Test
	void 참가자는_방과_참가자_상태를_본다() {
		battle(BattleStatus.LOBBY);

		BattleResponse response = service.get(ME, BATTLE_ID);

		assertThat(response.id()).isEqualTo(BATTLE_ID);
		assertThat(response.status()).isEqualTo(BattleStatus.LOBBY);
		assertThat(response.mode()).isEqualTo(BattleMode.RANKED);
		assertThat(response.course()).isEqualTo(new BattleResponse.Course(1L, "자료구조"));
		assertThat(response.unit()).isEqualTo(new BattleResponse.Unit(2L, "3주차"));
		assertThat(response.roomCode()).isEqualTo("K7M4QX");
		assertThat(response.participants()).extracting(BattleResponse.Participant::nickname).containsExactly("나", "친구");
		assertThat(response.participants()).extracting(BattleResponse.Participant::isMe).containsExactly(true, false);
		assertThat(response.summary()).isNull();
	}

	@Test
	void 참가자가_아니면_없는_배틀과_같은_NOT_FOUND다() {
		battle(BattleStatus.LOBBY);
		when(battleRepository.findById(404L)).thenReturn(Optional.empty());

		for (long id : new long[] {BATTLE_ID, 404L}) {
			Throwable thrown = catchThrowable(() -> service.get(99L, id));
			assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
		}
	}

	@Test
	void 입장_시간이_지난_대기실은_EXPIRED로_보여_준다() {
		Battle waiting = battle(BattleStatus.WAITING);
		ReflectionTestUtils.setField(waiting, "expiresAt", NOW.minusSeconds(5));

		assertThat(service.get(ME, BATTLE_ID).status()).isEqualTo(BattleStatus.EXPIRED);
	}

	@Test
	void 끝난_배틀은_내_점수와_상대_점수와_결과를_준다() {
		battle(BattleStatus.FINISHED);
		ReflectionTestUtils.setField(mine, "totalScore", new BigDecimal("3.00"));
		ReflectionTestUtils.setField(mine, "result", BattleResult.WIN);
		ReflectionTestUtils.setField(friends, "totalScore", new BigDecimal("1.20"));
		ReflectionTestUtils.setField(friends, "result", BattleResult.LOSE);

		BattleResponse.Summary summary = service.get(ME, BATTLE_ID).summary();

		assertThat(summary.myScore()).isEqualByComparingTo("3.00");
		assertThat(summary.opponentScore()).isEqualByComparingTo("1.20");
		assertThat(summary.result()).isEqualTo(BattleResult.WIN);
		assertThat(summary.eloDelta()).isNull();   // Elo 는 랭킹(WP10)이 생기면 채운다

		BattleResponse fromFriend = service.get(FRIEND, BATTLE_ID);
		assertThat(fromFriend.summary().result()).isEqualTo(BattleResult.LOSE);
		assertThat(fromFriend.summary().myScore()).isEqualByComparingTo("1.20");
	}

	@Test
	void 연습_배틀은_나만_있고_상대_점수와_결과와_방_코드가_없다() {
		Battle practice = Battle.startPractice(1L, 2L, 9L, 2, ME, NOW);
		ReflectionTestUtils.setField(practice, "id", 70L);
		ReflectionTestUtils.setField(practice, "status", BattleStatus.FINISHED);
		BattleParticipant only = BattleParticipant.practice(70L, ME, 100L, "[]", 0, NOW);
		ReflectionTestUtils.setField(only, "totalScore", new BigDecimal("1.50"));
		when(battleRepository.findById(70L)).thenReturn(Optional.of(practice));
		when(participantRepository.findByBattleIdOrderByIdAsc(70L)).thenReturn(List.of(only));

		BattleResponse response = service.get(ME, 70L);

		assertThat(response.mode()).isEqualTo(BattleMode.PRACTICE);
		assertThat(response.roomCode()).isNull();
		assertThat(response.expiresAt()).isNull();
		assertThat(response.participants()).hasSize(1);
		assertThat(response.summary().myScore()).isEqualByComparingTo("1.50");
		assertThat(response.summary().opponentScore()).isNull();
		assertThat(response.summary().result()).isNull();
		assertThat(response.summary().eloDelta()).isNull();
	}

	@Test
	@SuppressWarnings("unchecked")
	void 내_배틀_기록은_상대와_내_점수를_담아_준다() {
		Battle finished = battle(BattleStatus.FINISHED);
		ReflectionTestUtils.setField(mine, "totalScore", new BigDecimal("2.50"));
		ReflectionTestUtils.setField(mine, "result", BattleResult.DRAW);
		Battle waiting = Battle.openRoom(1L, 2L, 5, ME, "ABCDEF", NOW.plus(Duration.ofMinutes(10)));
		ReflectionTestUtils.setField(waiting, "id", 51L);
		ReflectionTestUtils.setField(waiting, "createdAt", NOW);
		BattleParticipant alone = BattleParticipant.joined(51L, ME, 100L, true, NOW);
		Page<Battle> page = new PageImpl<>(List.of(waiting, finished));
		when(battleRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
		when(participantRepository.findByBattleIdIn(anyCollection())).thenReturn(List.of(mine, friends, alone));

		PageResponse<BattleSummaryResponse> result = service.mine(ME, null, null, null, null, null);

		assertThat(result.items()).extracting(BattleSummaryResponse::id).containsExactly(51L, BATTLE_ID);
		BattleSummaryResponse withoutOpponent = result.items().get(0);
		assertThat(withoutOpponent.opponent()).isNull();
		assertThat(withoutOpponent.myScore()).isNull();   // 끝나지 않았으면 점수가 없다
		BattleSummaryResponse done = result.items().get(1);
		assertThat(done.opponent()).isEqualTo(new BattleSummaryResponse.Opponent(FRIEND, "친구"));
		assertThat(done.myScore()).isEqualByComparingTo("2.50");
		assertThat(done.result()).isEqualTo(BattleResult.DRAW);
		assertThat(done.course().name()).isEqualTo("자료구조");
		assertThat(done.unit().title()).isEqualTo("3주차");
	}

}
