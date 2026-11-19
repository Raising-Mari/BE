package com.aidisciple.domain.teaching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.aidisciple.domain.course.dto.DiscipleRef;
import com.aidisciple.domain.course.service.DiscipleService;
import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.entity.NoteStatus;
import com.aidisciple.domain.teaching.repository.NoteEntryRepository;

class UnitNoteCounterTest {

	private static final long USER_ID = 42L;
	private static final long COURSE_ID = 5L;
	private static final long DISCIPLE_ID = 9L;

	private final DiscipleService discipleService = mock(DiscipleService.class);
	private final NoteEntryRepository noteEntryRepository = mock(NoteEntryRepository.class);
	private final UnitNoteCounter counter = new UnitNoteCounter(discipleService, noteEntryRepository);

	private static NoteEntry note(long unitId) {
		return NoteEntry.create(DISCIPLE_ID, unitId, "노트 내용입니다", "근거", 1L, null, 1, "{}");
	}

	@Test
	void 내_제자의_ACTIVE_노트를_주차별로_센다() {
		when(discipleService.findByUserAndCourse(USER_ID, COURSE_ID)).thenReturn(Optional.of(new DiscipleRef(DISCIPLE_ID, USER_ID, COURSE_ID)));
		when(noteEntryRepository.findByDiscipleIdAndStatusOrderByUnitIdAscIdAsc(DISCIPLE_ID, NoteStatus.ACTIVE))
			.thenReturn(List.of(note(1), note(1), note(1), note(1), note(3)));

		assertThat(counter.activeNoteCounts(USER_ID, COURSE_ID)).isEqualTo(Map.of(1L, 4L, 3L, 1L));
	}

	@Test
	void 노트가_없으면_빈_맵이다() {
		when(discipleService.findByUserAndCourse(USER_ID, COURSE_ID)).thenReturn(Optional.of(new DiscipleRef(DISCIPLE_ID, USER_ID, COURSE_ID)));
		when(noteEntryRepository.findByDiscipleIdAndStatusOrderByUnitIdAscIdAsc(DISCIPLE_ID, NoteStatus.ACTIVE)).thenReturn(List.of());

		assertThat(counter.activeNoteCounts(USER_ID, COURSE_ID)).isEmpty();
	}

	@Test
	void 제자가_없으면_노트를_조회하지_않고_빈_맵이다() {
		when(discipleService.findByUserAndCourse(USER_ID, COURSE_ID)).thenReturn(Optional.empty());

		assertThat(counter.activeNoteCounts(USER_ID, COURSE_ID)).isEmpty();
		verify(noteEntryRepository, never()).findByDiscipleIdAndStatusOrderByUnitIdAscIdAsc(any(), any());
	}

}
