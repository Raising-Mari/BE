package com.aidisciple.domain.review.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.review.entity.ReviewEntry;

public interface ReviewEntryRepository extends JpaRepository<ReviewEntry, Long> {

	Optional<ReviewEntry> findByBattleAnswerId(Long battleAnswerId);

	/** R-01 에서 문항마다 따로 조회하지 않도록 한 번에 읽는다 */
	List<ReviewEntry> findByBattleAnswerIdIn(Collection<Long> battleAnswerIds);

}
