package com.aidisciple.domain.teaching.repository;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.entity.NoteStatus;

public interface NoteEntryRepository extends JpaRepository<NoteEntry, Long> {

	List<NoteEntry> findByDiscipleIdAndUnitIdAndStatusOrderByIdAsc(Long discipleId, Long unitId, NoteStatus status);

	List<NoteEntry> findByDiscipleIdAndStatusOrderByUnitIdAscIdAsc(Long discipleId, NoteStatus status);

	List<NoteEntry> findBySourceMessageIdOrderByIdAsc(Long sourceMessageId);

	/**
	 * T-05: 합칠 두 노트를 잠그고 읽는다. 같은 순간 가르치기 턴이 같은 노트를 고치면(UPDATE·REMOVE) 둘 중 하나가 기다린다.
	 * id 순으로 잠가 두 요청이 서로를 기다리는 일(교착)을 막는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select n from NoteEntry n where n.id in :ids order by n.id")
	List<NoteEntry> findAllForUpdate(@Param("ids") Collection<Long> ids);

}
