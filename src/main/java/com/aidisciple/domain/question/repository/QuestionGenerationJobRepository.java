package com.aidisciple.domain.question.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.question.entity.JobStatus;
import com.aidisciple.domain.question.entity.QuestionGenerationJob;

public interface QuestionGenerationJobRepository extends JpaRepository<QuestionGenerationJob, Long> {

	/** 같은 주차에 대기·실행 중인 작업이 있으면 새로 걸지 않는다 (AI 호출 절약) */
	boolean existsByUnitIdAndStatusIn(Long unitId, Collection<JobStatus> statuses);

	/** 서버 시작 시 복구(J-06) 대상 */
	List<QuestionGenerationJob> findByStatusIn(Collection<JobStatus> statuses);

}
