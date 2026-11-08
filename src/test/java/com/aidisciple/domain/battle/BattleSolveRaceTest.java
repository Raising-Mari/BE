package com.aidisciple.domain.battle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.event.BattleSolvedEvent;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.battle.service.BattleSolveStore;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.Disciple;
import com.aidisciple.domain.course.entity.Unit;
import com.aidisciple.domain.course.repository.CourseRepository;
import com.aidisciple.domain.course.repository.DiscipleRepository;
import com.aidisciple.domain.course.repository.UnitRepository;
import com.aidisciple.domain.grading.service.GradingExecutor;
import com.aidisciple.global.security.UserRole;

/**
 * 두 참가자가 거의 동시에 풀이를 끝내도 채점 이벤트가 빠지지 않는지 실제 DB 로 확인한다(경쟁 재현).
 * <p>
 * 결함: {@code complete()} 가 배틀 행을 잠그기 전에 참가자를 일반 읽기로 읽어, MySQL REPEATABLE READ 의 스냅샷이 잠금을 기다리기 전에
 * 고정됐다. 그래서 잠금을 얻은 뒤의 "둘 다 풀었는가" 확인이 상대의 방금 커밋을 못 보고, 두 트랜잭션이 모두 "아직 상대가 안 끝났다"고 보고
 * 이벤트를 내지 않았다(배틀이 서버를 다시 시작할 때까지 RUNNING 에 멈춘다).
 * <p>
 * 타이밍에 기대지 않도록 결정적으로 만든다: 이 스레드의 트랜잭션(T1)이 먼저 일반 읽기로 스냅샷을 고정하고, 다른 스레드(T2)가 상대의 풀이를
 * 커밋한 뒤에 T1 이 {@code complete()} 를 부른다. 수정 전 코드에서는 이 테스트가 실패한다.
 * <p>
 * 다른 스레드의 커밋이 보여야 해서 {@code @Transactional} 로 감쌀 수 없다. 대신 만든 행을 {@code @AfterEach} 에서 직접 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class BattleSolveRaceTest {

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

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
	BattleRepository battleRepository;

	@Autowired
	BattleParticipantRepository participantRepository;

	@Autowired
	BattleSolveStore solveStore;

	@Autowired
	ApplicationEvents events;

	/** 이벤트를 받은 채점이 실제로 돌아 테스트 데이터를 건드리지 않게 한다 */
	@MockitoBean
	GradingExecutor gradingExecutor;

	private long aliceId;
	private long bobId;
	private long courseId;
	private long unitId;
	private long aliceDiscipleId;
	private long bobDiscipleId;
	private long battleId;
	private long aliceParticipantId;
	private long bobParticipantId;

	@BeforeEach
	void 풀이_중인_두_참가자를_커밋해_둔다() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
			long schoolId = schoolRepository.findByCode("MJU").orElseThrow().getId();
			aliceId = newUser(schoolId, "a" + suffix);
			bobId = newUser(schoolId, "b" + suffix);
			String courseName = "경쟁시험" + suffix;
			courseId = courseRepository.saveAndFlush(Course.create(schoolId, null, courseName, courseName, "2026-2", aliceId)).getId();
			unitId = unitRepository.saveAndFlush(Unit.create(courseId, "1주차", "1주차", aliceId)).getId();
			aliceDiscipleId = discipleRepository.saveAndFlush(Disciple.create(aliceId, courseId, "마리", null)).getId();
			bobDiscipleId = discipleRepository.saveAndFlush(Disciple.create(bobId, courseId, "토리", null)).getId();

			Battle battle = Battle.openRoom(courseId, unitId, 3, aliceId, "ZZZZZ5", Instant.now());
			ReflectionTestUtils.setField(battle, "status", BattleStatus.RUNNING);
			ReflectionTestUtils.setField(battle, "activeRoomCode", null);
			battleId = battleRepository.saveAndFlush(battle).getId();
			aliceParticipantId = solvingParticipant(aliceId, aliceDiscipleId, true);
			bobParticipantId = solvingParticipant(bobId, bobDiscipleId, false);
		});
	}

	@AfterEach
	void 만든_행을_지운다() {
		jdbcTemplate.update("DELETE FROM battle_participants WHERE battle_id = ?", battleId);
		jdbcTemplate.update("DELETE FROM battles WHERE id = ?", battleId);
		jdbcTemplate.update("DELETE FROM disciples WHERE id IN (?, ?)", aliceDiscipleId, bobDiscipleId);
		jdbcTemplate.update("DELETE FROM units WHERE id = ?", unitId);
		jdbcTemplate.update("DELETE FROM courses WHERE id = ?", courseId);
		jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", aliceId, bobId);
	}

	@Test
	void 한쪽이_스냅샷을_잡은_뒤_상대가_커밋해도_뒤에_끝낸_쪽이_채점_이벤트를_낸다() throws Exception {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			participantRepository.findById(aliceParticipantId);   // T1: 일반 읽기로 스냅샷을 고정한다

			// T2: 다른 스레드가 상대(밥)의 풀이를 끝내고 커밋한다. 아직 앨리스가 안 끝났으니 이벤트는 나지 않는다.
			CompletableFuture<Void> other = CompletableFuture.runAsync(
				() -> solveStore.complete(bobParticipantId, List.of(), 1));
			join(other);

			// T1: 이제 앨리스가 끝낸다. 상대의 방금 커밋을 봐야 "둘 다 풀었다"가 된다.
			solveStore.complete(aliceParticipantId, List.of(), 1);
		});

		assertThat(events.stream(BattleSolvedEvent.class).toList()).containsExactly(new BattleSolvedEvent(battleId));
	}

	@Test
	void 순서대로_끝내는_평범한_경우도_이벤트가_한_번만_난다() {
		solveStore.complete(aliceParticipantId, List.of(), 1);
		assertThat(events.stream(BattleSolvedEvent.class).count()).isZero();

		solveStore.complete(bobParticipantId, List.of(), 1);

		assertThat(events.stream(BattleSolvedEvent.class).toList()).containsExactly(new BattleSolvedEvent(battleId));
	}

	// --- 도우미 ---

	private long solvingParticipant(long userId, long discipleId, boolean creator) {
		BattleParticipant participant = BattleParticipant.joined(battleId, userId, discipleId, creator, Instant.now());
		participant.markReady("[]", 0, false, Instant.now());
		participant.claimSolve();
		return participantRepository.saveAndFlush(participant).getId();
	}

	private long newUser(long schoolId, String tag) {
		return userRepository.saveAndFlush(User.create(schoolId, null, "sub-" + tag, tag + "@mju.ac.kr",
			tag, UserRole.STUDENT, Instant.now())).getId();
	}

	private static void join(CompletableFuture<Void> future) {
		try {
			future.get(30, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException("다른 스레드의 풀이 완료가 실패했습니다.", e);
		}
	}

}
