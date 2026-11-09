package com.aidisciple.domain.question.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aidisciple.domain.question.entity.Question;
import com.aidisciple.domain.question.entity.QuestionStatus;

public interface QuestionRepository extends JpaRepository<Question, Long> {

	/** 중복 검사용: 이 주차의 살아 있는 문항 */
	List<Question> findByUnitIdAndStatusIn(Long unitId, Collection<QuestionStatus> statuses);

	List<Question> findByIdInAndUnitId(Collection<Long> ids, Long unitId);

	/** Q-01: 이 학생이 아직 배틀에서 보지 않은 쓸 수 있는 문항 수 */
	@Query("""
		select count(q) from Question q
		where q.unitId = :unitId and q.status in :statuses
		  and not exists (select 1 from UserQuestionHistory h where h.questionId = q.id and h.userId = :userId)
		""")
	long countUnseen(@Param("unitId") Long unitId, @Param("statuses") Collection<QuestionStatus> statuses,
					 @Param("userId") Long userId);

	/** 배틀 문항 선택: 참가자 누구도 본 적 없는 문항을 id 순으로 (셔플은 서비스가 배틀 id 시드로 한다) */
	@Query("""
		select q from Question q
		where q.unitId = :unitId and q.status in :statuses
		  and not exists (select 1 from UserQuestionHistory h where h.questionId = q.id and h.userId in :userIds)
		order by q.id
		""")
	List<Question> findUnseenByAll(@Param("unitId") Long unitId,
								   @Param("statuses") Collection<QuestionStatus> statuses, @Param("userIds") Collection<Long> userIds);


	/** X-03 관리자 문항 목록. 조건이 null 이면 거르지 않는다(정렬은 Pageable 로). */
	@Query("""
		select q from Question q
		where (:unitId is null or q.unitId = :unitId)
		  and (:status is null or q.status = :status)
		""")
	Page<Question> search(@Param("unitId") Long unitId, @Param("status") QuestionStatus status, Pageable pageable);

}
