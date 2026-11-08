package com.aidisciple.domain.teaching.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aidisciple.domain.teaching.entity.ChatMessage;
import com.aidisciple.domain.teaching.entity.ChatRole;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	Optional<ChatMessage> findByDiscipleIdAndClientMessageId(Long discipleId, String clientMessageId);

	Optional<ChatMessage> findByDiscipleIdAndUnitIdAndTurnNoAndRole(Long discipleId, Long unitId, int turnNo,
																	ChatRole role);

	/** 이 (제자, 주차)에서 지금까지의 마지막 턴 번호. 대화가 없으면 0 이다. */
	@Query("""
		select coalesce(max(m.turnNo), 0) from ChatMessage m
		where m.discipleId = :discipleId and m.unitId = :unitId
		""")
	int findMaxTurnNo(@Param("discipleId") Long discipleId, @Param("unitId") Long unitId);

	/** 최근 대화를 최신 것부터. 프롬프트에는 서비스가 순서를 뒤집어 오래된 것부터 넣는다. */
	List<ChatMessage> findByDiscipleIdAndUnitIdOrderByIdDesc(Long discipleId, Long unitId, Pageable pageable);

	Page<ChatMessage> findByDiscipleId(Long discipleId, Pageable pageable);

	Page<ChatMessage> findByDiscipleIdAndUnitId(Long discipleId, Long unitId, Pageable pageable);

}
