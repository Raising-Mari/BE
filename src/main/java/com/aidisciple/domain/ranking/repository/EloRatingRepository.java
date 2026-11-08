package com.aidisciple.domain.ranking.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aidisciple.domain.ranking.entity.EloRating;

public interface EloRatingRepository extends JpaRepository<EloRating, Long> {

	/** Elo 를 갱신할 때 행을 잠그고 읽는다. 두 사람의 행은 항상 user_id 순으로 부른다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from EloRating r where r.userId = :userId and r.courseId = :courseId")
	Optional<EloRating> findLocked(@Param("userId") Long userId, @Param("courseId") Long courseId);

	Optional<EloRating> findByUserIdAndCourseId(Long userId, Long courseId);

	List<EloRating> findByUserIdOrderByUpdatedAtDesc(Long userId);

	List<EloRating> findByCourseIdAndUserIdIn(Long courseId, Collection<Long> userIds);

	/** K-01 학교 전체 순위: 한 판이라도 한 사람만. 정렬(레이팅 내림차순, 같으면 user_id 오름차순)은 Pageable 로 준다. */
	Page<EloRating> findByCourseIdAndGamesPlayedGreaterThanEqual(Long courseId, int minGames, Pageable pageable);

	/** K-01 학과 순위 */
	Page<EloRating> findByCourseIdAndGamesPlayedGreaterThanEqualAndUserIdIn(Long courseId, int minGames,
																			 Collection<Long> userIds, Pageable pageable);

	/** 내 앞에 있는 사람 수(레이팅이 더 높은 사람). 같은 레이팅은 아래 메서드가 센다. */
	long countByCourseIdAndGamesPlayedGreaterThanEqualAndRatingGreaterThan(Long courseId, int minGames, int rating);

	/** 레이팅이 같으면 user_id 가 작은 쪽이 앞이다. */
	long countByCourseIdAndGamesPlayedGreaterThanEqualAndRatingAndUserIdLessThan(Long courseId, int minGames, int rating,
																				  Long userId);

	long countByCourseIdAndGamesPlayedGreaterThanEqualAndUserIdInAndRatingGreaterThan(Long courseId, int minGames,
																						Collection<Long> userIds, int rating);

	long countByCourseIdAndGamesPlayedGreaterThanEqualAndUserIdInAndRatingAndUserIdLessThan(Long courseId, int minGames,
																							  Collection<Long> userIds, int rating,
																							  Long userId);

}
