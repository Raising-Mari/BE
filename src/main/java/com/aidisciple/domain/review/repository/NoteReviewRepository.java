package com.aidisciple.domain.review.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.review.entity.NoteReview;

public interface NoteReviewRepository extends JpaRepository<NoteReview, Long> {

	/** REVIEW_ALREADY_SUBMITTED: 이 배틀에 이미 리뷰를 썼는가 */
	boolean existsByBattleIdAndReviewerId(Long battleId, Long reviewerId);

	/** R-08: 내가 쓴 리뷰는 reviewer=나, 받은 리뷰는 reviewer=상대 (한 배틀은 두 사람이라) */
	Optional<NoteReview> findByBattleIdAndReviewerId(Long battleId, Long reviewerId);

}
