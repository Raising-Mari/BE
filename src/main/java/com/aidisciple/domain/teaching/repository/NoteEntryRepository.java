package com.aidisciple.domain.teaching.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.entity.NoteStatus;

public interface NoteEntryRepository extends JpaRepository<NoteEntry, Long> {

	List<NoteEntry> findByDiscipleIdAndUnitIdAndStatusOrderByIdAsc(Long discipleId, Long unitId, NoteStatus status);

	List<NoteEntry> findByDiscipleIdAndStatusOrderByUnitIdAscIdAsc(Long discipleId, NoteStatus status);

	List<NoteEntry> findBySourceMessageIdOrderByIdAsc(Long sourceMessageId);

}
