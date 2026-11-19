package com.aidisciple.domain.teaching.service;

import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.course.service.DiscipleService;
import com.aidisciple.domain.course.service.UnitNoteCountLookup;
import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.entity.NoteStatus;
import com.aidisciple.domain.teaching.repository.NoteEntryRepository;

/** C-05 {@code myNoteCount} 의 구현. 노트의 주인은 제자이므로 사용자·과목으로 제자를 찾아 그 제자의 ACTIVE 노트를 주차별로 센다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnitNoteCounter implements UnitNoteCountLookup {

	private final DiscipleService discipleService;
	private final NoteEntryRepository noteEntryRepository;

	@Override
	public Map<Long, Long> activeNoteCounts(long userId, long courseId) {
		return discipleService.findByUserAndCourse(userId, courseId)
			.map(disciple -> noteEntryRepository
				.findByDiscipleIdAndStatusOrderByUnitIdAscIdAsc(disciple.id(), NoteStatus.ACTIVE).stream()
				.collect(Collectors.groupingBy(NoteEntry::getUnitId, Collectors.counting())))
			.orElse(Map.of());
	}

}
