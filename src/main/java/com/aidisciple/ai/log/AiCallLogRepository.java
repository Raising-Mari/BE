package com.aidisciple.ai.log;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

	/** X-10: 이 시각 이후의 호출. 행은 [용도, 성공 여부, 시각, 입력 토큰, 출력 토큰] (날짜 묶기는 서비스가 한국 시간으로 한다) */
	@Query("""
		select l.purpose, l.success, l.createdAt, l.inputTokens, l.outputTokens
		from AiCallLog l where l.createdAt >= :since
		""")
	List<Object[]> findForStats(@Param("since") Instant since);

}
