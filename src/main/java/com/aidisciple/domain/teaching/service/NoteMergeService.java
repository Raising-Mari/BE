package com.aidisciple.domain.teaching.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.course.service.DiscipleService;
import com.aidisciple.domain.teaching.dto.MergeNotesRequest;
import com.aidisciple.domain.teaching.dto.MergeNotesResponse;
import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.repository.NoteEntryRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * T-05 노트 합치기. 내용은 바꾸지 않고 merge 노트의 상태만 MERGED(merged_into_entry_id = keep)로 바꾼다.
 * 두 노트는 이 제자의, 같은 주차의, 지금 ACTIVE 인 서로 다른 노트여야 한다(아니면 TEACHING_NOTE_MERGE_INVALID).
 * 서버는 제안을 저장하지 않으므로 여기서 유사도를 다시 따지지 않는다 — 학생이 고른 것을 따른다.
 */
@Service
@RequiredArgsConstructor
public class NoteMergeService {

	private final DiscipleService discipleService;
	private final NoteEntryRepository noteEntryRepository;

	@Transactional
	public MergeNotesResponse merge(long userId, long discipleId, MergeNotesRequest request) {
		discipleService.requireOwned(userId, discipleId);
		long keepId = request.keepEntryId();
		long mergeId = request.mergeEntryId();
		if (keepId == mergeId) {
			throw invalid();
		}
		Map<Long, NoteEntry> notes = noteEntryRepository.findAllForUpdate(List.of(keepId, mergeId)).stream()
			.collect(Collectors.toMap(NoteEntry::getId, Function.identity()));
		NoteEntry keep = notes.get(keepId);
		NoteEntry merge = notes.get(mergeId);
		if (keep == null || merge == null
			|| keep.getDiscipleId() != discipleId || merge.getDiscipleId() != discipleId
			|| !keep.getUnitId().equals(merge.getUnitId())
			|| !keep.isActive() || !merge.isActive()) {
			throw invalid();
		}
		merge.mergeInto(keepId);
		return new MergeNotesResponse(keepId, mergeId);
	}

	private static BusinessException invalid() {
		return new BusinessException(ErrorCode.TEACHING_NOTE_MERGE_INVALID);
	}

}
