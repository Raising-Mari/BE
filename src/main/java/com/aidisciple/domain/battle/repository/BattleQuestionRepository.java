package com.aidisciple.domain.battle.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.battle.entity.BattleQuestion;

public interface BattleQuestionRepository extends JpaRepository<BattleQuestion, Long> {

	List<BattleQuestion> findByBattleIdOrderBySeqAsc(Long battleId);

}
