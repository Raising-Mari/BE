package com.aidisciple.domain.teaching.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.aidisciple.domain.teaching.config.TeachingProperties;
import com.aidisciple.domain.teaching.logic.AcceptedChange.MergeCandidate;
import com.aidisciple.global.text.CharNgrams;
import com.aidisciple.global.text.NegationDetector;
import com.aidisciple.global.text.TextNormalizer;

/**
 * AI 가 제안한 노트 변경을 학생의 실제 메시지와 대조해 걸러 낸다. DB·AI 를 모르는 순수 로직이다. (api-spec 11-1 의 5단계)
 *
 * <p>변경은 AI 가 준 순서대로 하나씩 처리하고, 앞 변경의 결과(삭제·수정·새 노트)가 뒤 변경의 판단에 반영된다.
 * 새 노트의 id 는 저장할 때 정해지므로, 같은 턴에 만든 노트는 id 없는 "대기 노트"로 작업 목록에 둔다.
 *
 * <p>같은 턴에 AI 가 거의 같은 ADD 를 두 번 내면(유사도 ≥ 자동 병합 기준) 뒤의 것은 조용히 버린다.
 * 탈락이 아니라 중복이므로 기록하지 않는다. 대기 노트에는 id 가 없어 합치기 제안은 만들지 않는다.
 */
@Component
public class NoteChangeVerifier {

	public static final int NOTE_MIN_LENGTH = 5;
	public static final int NOTE_MAX_LENGTH = 300;

	/** 글자 3-gram. 노트 검증과 병합 판정이 같은 단위를 쓴다. */
	private static final int NGRAM = 3;

	private final TeachingProperties properties;

	public NoteChangeVerifier(TeachingProperties properties) {
		this.properties = properties;
	}

	/** 작업 목록의 노트. {@code persistedId} 가 null 이면 이번 턴에 새로 만든 노트다. */
	private record WorkingNote(Long persistedId, String content) {
	}

	/** 진행 중에 바뀌는 상태 */
	private static final class State {
		final List<WorkingNote> notes = new ArrayList<>();
		final List<AcceptedChange> accepted = new ArrayList<>();
		final List<RejectedChange> rejected = new ArrayList<>();
		boolean noteLimitReached;
	}

	public VerificationResult verify(String studentMessage, List<ActiveNote> activeNotes, List<ProposedNoteChange> proposed) {
		State state = new State();
		for (ActiveNote note : activeNotes) {
			state.notes.add(new WorkingNote(note.id(), note.content()));
		}
		for (ProposedNoteChange change : proposed) {
			if (change.op() == null) {
				state.rejected.add(new RejectedChange(change, List.of()));
				continue;
			}
			switch (change.op()) {
				case ADD -> verifyAdd(studentMessage, change, state);
				case UPDATE -> verifyUpdate(change, state);
				case REMOVE -> verifyRemove(change, state);
			}
		}
		return new VerificationResult(List.copyOf(state.accepted), List.copyOf(state.rejected), state.noteLimitReached);
	}

	private void verifyAdd(String studentMessage, ProposedNoteChange change, State state) {
		String content = strip(change.content());
		String evidence = strip(change.evidence());

		List<NoteCheckFailure> failures = new ArrayList<>();
		if (!evidenceInMessage(evidence, studentMessage)) {
			failures.add(NoteCheckFailure.EVIDENCE_NOT_IN_MESSAGE);
		}
		if (!content.isEmpty()) {
			if (CharNgrams.containment(content, studentMessage, NGRAM) < properties.minOverlap()) {
				failures.add(NoteCheckFailure.LOW_OVERLAP);
			}
			if (!NegationDetector.sameParity(evidence, content)) {
				failures.add(NoteCheckFailure.NEGATION_MISMATCH);
			}
		}
		if (!lengthValid(content)) {
			failures.add(NoteCheckFailure.LENGTH_INVALID);
		}
		if (!failures.isEmpty()) {
			state.rejected.add(new RejectedChange(change, failures));
			return;
		}

		// 유사 노트 처리 → 자동 병합이면 개수가 늘지 않으므로 상한 검사에서 제외 → 나머지 ADD 는 모두 상한 검사
		List<Long> mergedIds = new ArrayList<>();
		List<MergeCandidate> suggestions = new ArrayList<>();
		for (WorkingNote existing : state.notes) {
			double similarity = CharNgrams.similarity(content, existing.content(), NGRAM);
			if (similarity >= properties.mergeAutoThreshold()) {
				if (existing.persistedId() == null) {
					return;
				}
				mergedIds.add(existing.persistedId());
			} else if (similarity >= properties.mergeSuggestThreshold() && existing.persistedId() != null) {
				suggestions.add(new MergeCandidate(existing.persistedId(), similarity));
			}
		}

		if (mergedIds.isEmpty() && state.notes.size() >= properties.maxNotesPerUnit()) {
			state.noteLimitReached = true;
			state.rejected.add(new RejectedChange(change, List.of(NoteCheckFailure.NOTE_LIMIT_REACHED)));
			return;
		}

		state.notes.removeIf(note -> note.persistedId() != null && mergedIds.contains(note.persistedId()));
		state.notes.add(new WorkingNote(null, content));
		suggestions.sort(Comparator.comparingDouble(MergeCandidate::similarity).reversed());
		state.accepted.add(new AcceptedChange.Add(content, evidence, List.copyOf(mergedIds), List.copyOf(suggestions)));
	}

	/** 수정·삭제는 학생이 막히지 않도록 검증을 줄였다. 새 content 는 길이만 본다. (알려진 위험은 프롬프트로 통제, api-spec 11-1) */
	private void verifyUpdate(ProposedNoteChange change, State state) {
		String content = strip(change.content());
		List<NoteCheckFailure> failures = new ArrayList<>();
		if (findPersisted(state, change.targetId()) == null) {
			failures.add(NoteCheckFailure.TARGET_NOT_FOUND);
		}
		if (!lengthValid(content)) {
			failures.add(NoteCheckFailure.LENGTH_INVALID);
		}
		if (!failures.isEmpty()) {
			state.rejected.add(new RejectedChange(change, failures));
			return;
		}
		WorkingNote target = findPersisted(state, change.targetId());
		state.notes.remove(target);
		state.notes.add(new WorkingNote(null, content));
		state.accepted.add(new AcceptedChange.Update(change.targetId(), content));
	}

	private void verifyRemove(ProposedNoteChange change, State state) {
		WorkingNote target = findPersisted(state, change.targetId());
		if (target == null) {
			state.rejected.add(new RejectedChange(change, List.of(NoteCheckFailure.TARGET_NOT_FOUND)));
			return;
		}
		state.notes.remove(target);
		state.accepted.add(new AcceptedChange.Remove(change.targetId()));
	}

	/** 이번 턴에 이미 고치거나 지운 노트는 작업 목록에 없으므로 같은 노트를 두 번 건드리면 찾지 못한다. */
	private static WorkingNote findPersisted(State state, Long targetId) {
		if (targetId == null) {
			return null;
		}
		return state.notes.stream()
			.filter(note -> targetId.equals(note.persistedId()))
			.findFirst()
			.orElse(null);
	}

	/** 근거가 비어 있으면 "비어 있는 문자열은 어디에나 들어 있다"로 통과하지 않게 먼저 걸러낸다. */
	private static boolean evidenceInMessage(String evidence, String studentMessage) {
		String normalizedEvidence = TextNormalizer.normalize(evidence);
		return !normalizedEvidence.isEmpty() && TextNormalizer.normalize(studentMessage).contains(normalizedEvidence);
	}

	private static boolean lengthValid(String content) {
		int length = content.codePointCount(0, content.length());
		return length >= NOTE_MIN_LENGTH && length <= NOTE_MAX_LENGTH;
	}

	private static String strip(String text) {
		return text == null ? "" : text.strip();
	}

}
