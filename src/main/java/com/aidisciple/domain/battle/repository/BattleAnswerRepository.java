package com.aidisciple.domain.battle.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.battle.entity.BattleAnswer;

public interface BattleAnswerRepository extends JpaRepository<BattleAnswer, Long> {

	List<BattleAnswer> findByParticipantId(Long participantId);

	List<BattleAnswer> findByParticipantIdIn(Collection<Long> participantIds);

	boolean existsByParticipantId(Long participantId);

}
