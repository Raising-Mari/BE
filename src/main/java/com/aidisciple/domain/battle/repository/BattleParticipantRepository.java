package com.aidisciple.domain.battle.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.battle.entity.BattleParticipant;

public interface BattleParticipantRepository extends JpaRepository<BattleParticipant, Long> {

	List<BattleParticipant> findByBattleIdOrderByIdAsc(Long battleId);

	List<BattleParticipant> findByBattleIdIn(Collection<Long> battleIds);

	Optional<BattleParticipant> findByBattleIdAndUserId(Long battleId, Long userId);

}
