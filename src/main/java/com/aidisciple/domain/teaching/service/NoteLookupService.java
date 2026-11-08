package com.aidisciple.domain.teaching.service;

import java.util.Collection;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.teaching.dto.NoteRef;
import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.entity.NoteStatus;
import com.aidisciple.domain.teaching.repository.NoteEntryRepository;

/**
 * 다른 도메인(배틀)이 제자의 노트를 읽는 통로. 소유 확인은 부르는 쪽이 이미 했다고 보고 하지 않는다 —
 * 노트 주인은 제자(disciple)이고, 배틀은 자기 참가자의 제자 id 로만 부른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoteLookupService {

	private final NoteEntryRepository noteEntryRepository;

	/** 그 주차의 ACTIVE 노트를 오래된 것부터 */
	public List<NoteRef> activeNotes(long discipleId, long unitId) {
		return noteEntryRepository.findByDiscipleIdAndUnitIdAndStatusOrderByIdAsc(discipleId, unitId, NoteStatus.ACTIVE)
			.stream()
			.map(NoteLookupService::toRef)
			.toList();
	}

	/** 스냅샷해 둔 노트 id 로 내용을 읽는다. 지금은 ACTIVE 가 아니어도(고쳐 말해서 대체됐어도) 그 순간의 노트를 준다. id 순. */
	public List<NoteRef> findByIds(Collection<Long> noteIds) {
		if (noteIds.isEmpty()) {
			return List.of();
		}
		return noteEntryRepository.findAllById(noteIds).stream()
			.sorted((a, b) -> Long.compare(a.getId(), b.getId()))
			.map(NoteLookupService::toRef)
			.toList();
	}

	private static NoteRef toRef(NoteEntry note) {
		return new NoteRef(note.getId(), note.getContent());
	}

}
