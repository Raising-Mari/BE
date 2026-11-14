package com.aidisciple.domain.battle.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.Verdict;

public interface BattleQuestionRepository extends JpaRepository<BattleQuestion, Long> {

	List<BattleQuestion> findByBattleIdOrderBySeqAsc(Long battleId);

	/** X-03 통계: 문항마다 배틀(연습 포함)에 나온 횟수. 행은 [questionId, 횟수] */
	@Query("select bq.questionId, count(bq) from BattleQuestion bq where bq.questionId in :questionIds group by bq.questionId")
	List<Object[]> countUsage(@Param("questionIds") Collection<Long> questionIds);

	/** X-03 통계: 문항마다 채점된 답 수와 정답 수. 채점 전(PENDING)은 뺀다. 행은 [questionId, 답 수, 정답 수] */
	@Query("""
		select bq.questionId, count(a), sum(case when a.verdict = :correct then 1 else 0 end)
		from BattleAnswer a join BattleQuestion bq on bq.id = a.battleQuestionId
		where bq.questionId in :questionIds and a.verdict <> :pending
		group by bq.questionId
		""")
	List<Object[]> countAnswers(@Param("questionIds") Collection<Long> questionIds, @Param("correct") Verdict correct,
								@Param("pending") Verdict pending);

}
