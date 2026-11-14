package com.aidisciple.domain.question.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aidisciple.domain.question.entity.QuestionEvidence;

public interface QuestionEvidenceRepository extends JpaRepository<QuestionEvidence, QuestionEvidence.Key> {

	/** 이 문장들을 근거로 쓴 문항 id (자료 연결이 풀린 주차의 문항을 RETIRED 로 바꿀 때) */
	@Query("select distinct e.id.questionId from QuestionEvidence e where e.id.sentenceId in :sentenceIds")
	List<Long> findQuestionIdsBySentenceIds(@Param("sentenceIds") Collection<Long> sentenceIds);


	/** 복기(R-01)에서 문항마다 근거 문장을 한 번에 읽는다 */
	List<QuestionEvidence> findByIdQuestionIdIn(Collection<Long> questionIds);

}
