package com.aidisciple.domain.battle.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aidisciple.domain.battle.entity.BattleParticipant;

public interface BattleParticipantRepository extends JpaRepository<BattleParticipant, Long> {

	List<BattleParticipant> findByBattleIdOrderByIdAsc(Long battleId);

	/**
	 * 참가자를 <b>잠그고</b> 읽는다(FOR UPDATE). 잠금 읽기는 이 트랜잭션의 스냅샷이 아니라 지금 커밋된 최신 값을 본다.
	 * MySQL 기본 격리(REPEATABLE READ)에서는 트랜잭션의 첫 일반 읽기가 스냅샷을 고정하므로, 배틀 행 잠금을 기다리는 사이
	 * 다른 참가자가 커밋한 풀이 완료를 일반 읽기로는 못 본다. "둘 다 풀었는가"처럼 다른 참가자의 상태로 판단할 때는 이 메서드를 쓴다.
	 * 항상 배틀 행을 잠근 뒤에 부르고 id 순으로 잠가, 다른 곳(준비·재시도·정산)과 같은 순서를 지킨다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from BattleParticipant p where p.battleId = :battleId order by p.id")
	List<BattleParticipant> findLockedByBattleId(@Param("battleId") Long battleId);

	List<BattleParticipant> findByBattleIdIn(Collection<Long> battleIds);

	Optional<BattleParticipant> findByBattleIdAndUserId(Long battleId, Long userId);

}
