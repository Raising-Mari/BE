package com.aidisciple.domain.teaching.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.course.dto.DiscipleRef;
import com.aidisciple.domain.course.dto.UnitRef;
import com.aidisciple.domain.course.service.DiscipleService;
import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.domain.teaching.config.TeachingProperties;
import com.aidisciple.domain.teaching.dto.ChatMessageResponse;
import com.aidisciple.domain.teaching.dto.NotesResponse;
import com.aidisciple.domain.teaching.entity.ChatMessage;
import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.entity.NoteStatus;
import com.aidisciple.domain.teaching.repository.ChatMessageRepository;
import com.aidisciple.domain.teaching.repository.NoteEntryRepository;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.api.Pages;

/** T-02 대화 내역, T-03 현재 노트. 둘 다 제자 주인만 볼 수 있다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeachingQueryService {

	private final ChatMessageRepository chatMessageRepository;
	private final NoteEntryRepository noteEntryRepository;
	private final DiscipleService discipleService;
	private final UnitService unitService;
	private final TeachingProperties properties;

	/** T-02. 최신 메시지가 먼저 오는 페이지다 (0페이지 = 가장 최근). FE 는 화면에 그릴 때 순서를 뒤집는다. */
	public PageResponse<ChatMessageResponse> messages(long userId, long discipleId, Long unitId, Integer page,
													  Integer size) {
		DiscipleRef disciple = discipleService.requireOwned(userId, discipleId);
		Pageable pageable = Pages.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
		Page<ChatMessage> messages;
		if (unitId == null) {
			messages = chatMessageRepository.findByDiscipleId(disciple.id(), pageable);
		} else {
			unitService.requireInCourse(disciple.courseId(), unitId);
			messages = chatMessageRepository.findByDiscipleIdAndUnitId(disciple.id(), unitId, pageable);
		}
		return PageResponse.from(messages, TeachingQueryService::toMessageResponse);
	}

	/**
	 * T-03. unitId 를 주면 노트가 없어도 그 주차 1개 그룹을, 안 주면 노트가 있는 주차만 unit.id 오름차순으로 준다.
	 */
	public NotesResponse notes(long userId, long discipleId, Long unitId) {
		DiscipleRef disciple = discipleService.requireOwned(userId, discipleId);
		if (unitId != null) {
			UnitRef unit = unitService.requireInCourse(disciple.courseId(), unitId);
			List<NoteEntry> notes = noteEntryRepository.findByDiscipleIdAndUnitIdAndStatusOrderByIdAsc(disciple.id(),
				unit.id(), NoteStatus.ACTIVE);
			return new NotesResponse(List.of(toGroup(unit, notes)));
		}

		Map<Long, List<NoteEntry>> notesByUnit = noteEntryRepository
			.findByDiscipleIdAndStatusOrderByUnitIdAscIdAsc(disciple.id(), NoteStatus.ACTIVE).stream()
			.collect(Collectors.groupingBy(NoteEntry::getUnitId, LinkedHashMap::new, Collectors.toList()));
		Map<Long, UnitRef> units = unitService.findRefs(notesByUnit.keySet());
		List<NotesResponse.Group> groups = notesByUnit.entrySet().stream()
			.filter(entry -> units.containsKey(entry.getKey()))
			.map(entry -> toGroup(units.get(entry.getKey()), entry.getValue()))
			.toList();
		return new NotesResponse(groups);
	}

	private NotesResponse.Group toGroup(UnitRef unit, List<NoteEntry> notes) {
		List<NotesResponse.Item> items = notes.stream()
			.map(note -> new NotesResponse.Item(note.getId(), note.getContent(), note.getCreatedAt(), note.getVersion(),
				note.getSourceMessageId()))
			.toList();
		return new NotesResponse.Group(new NotesResponse.Unit(unit.id(), unit.title()), items.size(),
			properties.maxNotesPerUnit(), items);
	}

	private static ChatMessageResponse toMessageResponse(ChatMessage message) {
		return new ChatMessageResponse(message.getId(), message.getRole(), message.getUnitId(), message.getTurnNo(),
			message.getContent(), message.isAskedWhy(), message.getCreatedAt());
	}

}
