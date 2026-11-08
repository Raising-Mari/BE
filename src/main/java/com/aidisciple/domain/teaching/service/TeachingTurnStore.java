package com.aidisciple.domain.teaching.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import com.aidisciple.domain.course.dto.DiscipleRef;
import com.aidisciple.domain.course.dto.UnitRef;
import com.aidisciple.domain.course.service.DiscipleService;
import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.domain.teaching.config.TeachingProperties;
import com.aidisciple.domain.teaching.dto.SendMessageRequest;
import com.aidisciple.domain.teaching.dto.TeachingTurnResponse;
import com.aidisciple.domain.teaching.entity.ChatMessage;
import com.aidisciple.domain.teaching.entity.ChatRole;
import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.entity.NoteOp;
import com.aidisciple.domain.teaching.entity.NoteStatus;
import com.aidisciple.domain.teaching.entity.RejectedNoteChange;
import com.aidisciple.domain.teaching.logic.AcceptedChange;
import com.aidisciple.domain.teaching.logic.ActiveNote;
import com.aidisciple.domain.teaching.logic.NoteCheckFailure;
import com.aidisciple.domain.teaching.logic.ProposedNoteChange;
import com.aidisciple.domain.teaching.logic.RejectedChange;
import com.aidisciple.domain.teaching.logic.VerificationResult;
import com.aidisciple.domain.teaching.repository.ChatMessageRepository;
import com.aidisciple.domain.teaching.repository.NoteEntryRepository;
import com.aidisciple.domain.teaching.repository.RejectedNoteChangeRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * T-01 의 DB 단계 (api-spec 11-1 의 1·2단계와 6단계). AI 호출은 하지 않는다.
 * {@link TeachingService} 가 prepare → (트랜잭션 밖에서 AI) → save 순서로 부른다. 메서드마다 짧은 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
public class TeachingTurnStore {

	/** UPDATE 는 새 내용을 검증하지 않으므로 검증 결과 대신 이 값을 남긴다 (erd 4절) */
	private static final String UPDATE_DETAIL = "{\"skipped\":true,\"op\":\"UPDATE\"}";

	private final ChatMessageRepository chatMessageRepository;
	private final NoteEntryRepository noteEntryRepository;
	private final RejectedNoteChangeRepository rejectedNoteChangeRepository;
	private final DiscipleService discipleService;
	private final UnitService unitService;
	private final TeachingProperties properties;
	private final JsonMapper jsonMapper;

	@Transactional(readOnly = true)
	public TurnPreparation prepare(long userId, long discipleId, SendMessageRequest request) {
		DiscipleRef disciple = discipleService.requireOwned(userId, discipleId);
		UnitRef unit = unitService.requireInCourse(disciple.courseId(), request.unitId());

		Optional<ChatMessage> alreadySent = chatMessageRepository.findByDiscipleIdAndClientMessageId(disciple.id(),
			request.clientMessageId());
		if (alreadySent.isPresent()) {
			return TurnPreparation.replayOf(replay(alreadySent.get()));
		}

		int turnNo = chatMessageRepository.findMaxTurnNo(disciple.id(), unit.id()) + 1;
		List<ChatMessage> newestFirst = chatMessageRepository.findByDiscipleIdAndUnitIdOrderByIdDesc(disciple.id(),
			unit.id(), PageRequest.of(0, properties.recentMessages()));
		List<TurnInput.ChatLine> recent = new ArrayList<>();
		for (int i = newestFirst.size() - 1; i >= 0; i--) {
			ChatMessage message = newestFirst.get(i);
			recent.add(new TurnInput.ChatLine(message.getRole(), message.getContent()));
		}
		List<ActiveNote> activeNotes = findActiveNotes(disciple.id(), unit.id()).stream()
			.map(note -> new ActiveNote(note.getId(), note.getContent()))
			.toList();

		TurnInput input = new TurnInput(userId, disciple.id(), unit.title(), turnNo, request.content(), recent,
			activeNotes);
		return new TurnPreparation(input, disciple.id(), unit.id(), null);
	}

	@Transactional
	public TeachingTurnResponse save(TurnPreparation preparation, SendMessageRequest request, TurnOutcome outcome) {
		long discipleId = preparation.discipleId();
		long unitId = preparation.unitId();
		int turnNo = preparation.input().turnNo();

		ChatMessage student;
		ChatMessage disciple;
		try {
			student = chatMessageRepository.saveAndFlush(
				ChatMessage.student(discipleId, unitId, turnNo, request.content(), request.clientMessageId()));
			disciple = chatMessageRepository.saveAndFlush(
				ChatMessage.disciple(discipleId, unitId, turnNo, outcome.reply(), outcome.askedWhy()));
		} catch (DataIntegrityViolationException e) {
			// 같은 턴 번호(동시 전송) 또는 같은 clientMessageId 가 먼저 저장됐다. 재시도하면 저장된 결과를 받는다.
			throw new BusinessException(ErrorCode.TEACHING_CONCURRENT_TURN);
		}

		// 검증은 읽기 단계의 노트로 했지만, 상태를 바꿀 엔티티는 이 트랜잭션에서 다시 읽는다.
		Map<Long, NoteEntry> active = new HashMap<>();
		for (NoteEntry note : findActiveNotes(discipleId, unitId)) {
			active.put(note.getId(), note);
		}

		VerificationResult verification = outcome.verification();
		List<TeachingTurnResponse.AppliedChange> applied = new ArrayList<>();
		List<TeachingTurnResponse.MergedNote> merged = new ArrayList<>();
		List<TeachingTurnResponse.MergeSuggestion> suggestions = new ArrayList<>();

		for (AcceptedChange change : verification.accepted()) {
			switch (change) {
				case AcceptedChange.Add add -> {
					NoteEntry note = noteEntryRepository.save(NoteEntry.create(discipleId, unitId, add.content(),
						add.evidence(), student.getId(), null, 1,
						toJson(Map.of("op", "ADD", "passed", true, "mergedCount", add.mergedNoteIds().size()))));
					applied.add(new TeachingTurnResponse.AppliedChange(NoteOp.ADD, note.getId(), null, note.getContent()));
					for (Long mergedId : add.mergedNoteIds()) {
						NoteEntry old = active.remove(mergedId);
						if (old != null) {
							old.mergeInto(note.getId());
							merged.add(new TeachingTurnResponse.MergedNote(old.getId(), note.getId()));
						}
					}
					for (AcceptedChange.MergeCandidate candidate : add.suggestions()) {
						NoteEntry existing = active.get(candidate.noteId());
						if (existing != null) {
							suggestions.add(new TeachingTurnResponse.MergeSuggestion(note.getId(), existing.getId(),
								existing.getContent(), candidate.similarity()));
						}
					}
				}
				case AcceptedChange.Update update -> {
					NoteEntry old = active.remove(update.targetId());
					if (old != null) {
						// UPDATE 의 근거는 이번 학생 메시지 전체로 남긴다 (검증기가 UPDATE 에는 근거 구절을 따로 주지 않는다)
						NoteEntry note = noteEntryRepository.save(NoteEntry.create(discipleId, unitId, update.content(),
							request.content(), student.getId(), old.getId(), old.getVersion() + 1, UPDATE_DETAIL));
						old.supersede();
						applied.add(new TeachingTurnResponse.AppliedChange(NoteOp.UPDATE, note.getId(), old.getId(),
							note.getContent()));
					}
				}
				case AcceptedChange.Remove remove -> {
					NoteEntry old = active.remove(remove.targetId());
					if (old != null) {
						old.remove();
						applied.add(new TeachingTurnResponse.AppliedChange(NoteOp.REMOVE, old.getId(), null, null));
					}
				}
			}
		}

		for (RejectedChange rejected : verification.rejected()) {
			ProposedNoteChange proposal = rejected.change();
			// 없는 노트 id 를 그대로 넣으면 외래키에 걸리므로 TARGET_NOT_FOUND 면 비워 둔다
			boolean targetMissing = rejected.failedChecks().contains(NoteCheckFailure.TARGET_NOT_FOUND);
			rejectedNoteChangeRepository.save(RejectedNoteChange.create(discipleId, student.getId(), proposal.op(),
				targetMissing ? null : proposal.targetId(), proposal.content(), proposal.evidence(),
				toJson(rejected.failedChecks().stream().map(Enum::name).toList())));
		}

		TeachingTurnResponse.NoteChanges noteChanges = new TeachingTurnResponse.NoteChanges(applied, merged,
			suggestions, verification.rejected().size(), verification.noteLimitReached());
		return toResponse(student, disciple, noteChanges);
	}

	/**
	 * 이미 처리한 메시지를 다시 받았을 때의 응답. 새로 생긴 노트(ADD·UPDATE)와 탈락 개수는 다시 만들 수 있지만,
	 * 그 턴의 병합·삭제·합치기 제안은 따로 저장하지 않아서 빈 목록으로 준다.
	 */
	private TeachingTurnResponse replay(ChatMessage student) {
		ChatMessage disciple = chatMessageRepository.findByDiscipleIdAndUnitIdAndTurnNoAndRole(student.getDiscipleId(),
				student.getUnitId(), student.getTurnNo(), ChatRole.DISCIPLE)
			.orElseThrow(() -> new BusinessException(ErrorCode.TEACHING_CONCURRENT_TURN));
		List<TeachingTurnResponse.AppliedChange> applied = noteEntryRepository
			.findBySourceMessageIdOrderByIdAsc(student.getId()).stream()
			.map(note -> new TeachingTurnResponse.AppliedChange(
				note.getReplacesEntryId() == null ? NoteOp.ADD : NoteOp.UPDATE,
				note.getId(), note.getReplacesEntryId(), note.getContent()))
			.toList();
		int rejectedCount = (int) rejectedNoteChangeRepository.countByMessageId(student.getId());
		return toResponse(student, disciple,
			new TeachingTurnResponse.NoteChanges(applied, List.of(), List.of(), rejectedCount, false));
	}

	private List<NoteEntry> findActiveNotes(long discipleId, long unitId) {
		return noteEntryRepository.findByDiscipleIdAndUnitIdAndStatusOrderByIdAsc(discipleId, unitId,
			NoteStatus.ACTIVE);
	}

	private String toJson(Object value) {
		return jsonMapper.writeValueAsString(value);
	}

	/** pasteWarning(자료 원문과 겹침)은 아직 계산하지 않아 false 다. 자료 도메인에 비교 창구가 생기면 연결한다. */
	private static TeachingTurnResponse toResponse(ChatMessage student, ChatMessage disciple,
												   TeachingTurnResponse.NoteChanges noteChanges) {
		return new TeachingTurnResponse(student.getTurnNo(),
			new TeachingTurnResponse.StudentMessage(student.getId(), student.getContent(), student.getCreatedAt()),
			new TeachingTurnResponse.DiscipleMessage(disciple.getId(), disciple.getContent(), disciple.isAskedWhy(),
				disciple.getCreatedAt()),
			noteChanges, false);
	}

}
