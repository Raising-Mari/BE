package com.aidisciple.domain.ranking.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.ranking.entity.EloHistory;

public interface EloHistoryRepository extends JpaRepository<EloHistory, Long> {

	boolean existsByBattleId(Long battleId);

	List<EloHistory> findByBattleIdIn(Collection<Long> battleIds);

	Optional<EloHistory> findByBattleIdAndUserId(Long battleId, Long userId);

	/** K-07 내 Elo 변동 이력, 최신 것부터 */
	Page<EloHistory> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

	Page<EloHistory> findByUserIdAndCourseIdOrderByIdDesc(Long userId, Long courseId, Pageable pageable);

	/** K-05 성장폭 순위: 이 과목에서 기간 안에 쌓인 변동 전부(합은 서비스가 사용자별로 낸다) */
	List<EloHistory> findByCourseIdAndCreatedAtGreaterThanEqual(Long courseId, Instant since);

}
