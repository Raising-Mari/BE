package com.aidisciple.domain.question.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aidisciple.domain.question.entity.QuestionValidationLog;

public interface QuestionValidationLogRepository extends JpaRepository<QuestionValidationLog, Long> {

	/** X-04: 이 문항이 통과한 단계별 검증 기록 (탈락 후보는 questionId 가 없어 여기 나오지 않는다) */
	List<QuestionValidationLog> findByQuestionIdOrderByIdAsc(Long questionId);

	/** X-02: 작업의 단계별 탈락 수. 행은 [단계, 수] */
	@Query("""
		select l.stage, count(l) from QuestionValidationLog l
		where l.jobId = :jobId and l.passed = false
		group by l.stage
		""")
	List<Object[]> countRejectedByStage(@Param("jobId") Long jobId);

}
