package com.aidisciple.domain.battle.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleStatus;

/** 내 배틀 기록(B-06)은 조건이 선택적이라 {@link JpaSpecificationExecutor} 로 조립한다. */
public interface BattleRepository extends JpaRepository<Battle, Long>, JpaSpecificationExecutor<Battle> {

	/**
	 * 상태를 바꾸는 모든 요청(입장·준비·취소·재시도·풀이 완료)이 이 잠금부터 잡는다.
	 * 같은 배틀의 요청이 한 줄로 서서, "둘 다 준비되면 한 번만 시작" 같은 판단이 겹치지 않는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Battle b where b.id = :id")
	Optional<Battle> findLockedById(@Param("id") Long id);

	Optional<Battle> findByActiveRoomCode(String activeRoomCode);

	/** 만료·취소·종료된 방도 코드가 남아 있어 가장 최근 방을 찾는다. */
	Optional<Battle> findFirstByRoomCodeOrderByIdDesc(String roomCode);

	/**
	 * 사용자가 참가자로 들어 있는, 해당 모드의 진행 중 배틀이 있는가. 입장 시간이 지난 방은 만료 작업(J-04)이
	 * 아직 안 돌았어도 진행 중으로 치지 않는다 — 방금 만료된 방 때문에 새 방을 못 만드는 일이 없게.
	 */
	@Query("""
		select count(b) > 0 from Battle b
		where b.mode = :mode
		  and ((b.status in :openStatuses and b.expiresAt > :now) or b.status in :startedStatuses)
		  and exists (select 1 from BattleParticipant p where p.battleId = b.id and p.userId = :userId)
		""")
	boolean existsInProgress(@Param("userId") Long userId, @Param("mode") BattleMode mode,
							 @Param("openStatuses") Collection<BattleStatus> openStatuses,
							 @Param("startedStatuses") Collection<BattleStatus> startedStatuses, @Param("now") Instant now);

	default boolean hasInProgress(Long userId, BattleMode mode, Instant now) {
		return existsInProgress(userId, mode, BattleStatus.OPEN_ROOM, BattleStatus.STARTED, now);
	}

	/** J-04: 입장 시간이 지난 방의 id. 한 건씩 잠그고 처리하려고 id 만 가져온다. */
	@Query("select b.id from Battle b where b.status in :statuses and b.expiresAt <= :now order by b.id")
	List<Long> findIdsExpiredBefore(@Param("statuses") Collection<BattleStatus> statuses, @Param("now") Instant now);

	/** J-06: 서버가 시작될 때 이어서 처리할 배틀(RUNNING)의 id */
	@Query("select b.id from Battle b where b.status = :status order by b.id")
	List<Long> findIdsByStatus(@Param("status") BattleStatus status);

}
