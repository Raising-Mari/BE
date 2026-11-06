package com.aidisciple.domain.teaching.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aidisciple.domain.teaching.config.TeachingProperties;
import com.aidisciple.domain.teaching.entity.NoteOp;
import com.aidisciple.domain.teaching.logic.AcceptedChange.Add;
import com.aidisciple.domain.teaching.logic.AcceptedChange.Remove;
import com.aidisciple.domain.teaching.logic.AcceptedChange.Update;

/**
 * 노트 변경 검증의 통과·탈락 사례. 문장 사이 유사도는 실제 3-gram Dice 값을 계산해 고른 것이다.
 *   STACK ↔ "…구조야" 0.909 (자동 병합), "…자료구조이다" 0.833 (확인 카드), "…데이터가 …" 0.722 (해당 없음)
 */
class NoteChangeVerifierTest {

	private static final String STACK = "스택은 나중에 넣은 것이 먼저 나오는 구조이다";
	private static final String STACK_NEAR_DUPLICATE = "스택은 나중에 넣은 것이 먼저 나오는 구조야";            // 유사도 0.909
	private static final String STACK_SIMILAR = "스택은 나중에 넣은 것이 먼저 나오는 자료구조이다";            // 유사도 0.833
	private static final String STACK_DIFFERENT_ENOUGH = "스택은 나중에 넣은 데이터가 먼저 나오는 구조이다";   // 유사도 0.722
	private static final String QUEUE = "큐는 먼저 넣은 것이 먼저 나오는 구조이다";

	private final NoteChangeVerifier verifier = new NoteChangeVerifier(TeachingProperties.defaults());

	private static ProposedNoteChange add(String content, String evidence) {
		return new ProposedNoteChange(NoteOp.ADD, null, content, evidence);
	}

	private static ProposedNoteChange update(Long targetId, String content) {
		return new ProposedNoteChange(NoteOp.UPDATE, targetId, content, null);
	}

	private static ProposedNoteChange remove(Long targetId) {
		return new ProposedNoteChange(NoteOp.REMOVE, targetId, null, null);
	}

	/** 1번 노트는 STACK, 나머지는 서로 다른 필러. 합쳐서 {@code total}개. */
	private static List<ActiveNote> notes(int total) {
		List<ActiveNote> notes = new ArrayList<>();
		notes.add(new ActiveNote(1, STACK));
		for (int id = 2; id <= total; id++) {
			notes.add(new ActiveNote(id, "필러 노트 " + id + "번은 전혀 다른 주제를 설명한다"));
		}
		return notes;
	}

	// --- ADD 검사 ---

	@Test
	void 학생이_말한_내용을_그대로_옮긴_ADD는_통과한다() {
		VerificationResult result = verifier.verify(STACK + ". 그래서 되돌리기에 쓰여.", List.of(), List.of(add(STACK, STACK)));

		assertThat(result.rejected()).isEmpty();
		assertThat(result.accepted()).singleElement().isInstanceOfSatisfying(Add.class, added -> {
			assertThat(added.content()).isEqualTo(STACK);
			assertThat(added.mergedNoteIds()).isEmpty();
			assertThat(added.suggestions()).isEmpty();
		});
	}

	@Test
	void 근거가_학생_메시지에_없으면_탈락한다() {
		VerificationResult result = verifier.verify(STACK, List.of(), List.of(add(STACK, "큐는 먼저 넣은 것이 먼저 나온다")));

		assertThat(result.accepted()).isEmpty();
		assertThat(result.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).contains(NoteCheckFailure.EVIDENCE_NOT_IN_MESSAGE));
	}

	@Test
	void 근거가_비어_있으면_탈락한다() {
		VerificationResult blank = verifier.verify(STACK, List.of(), List.of(add(STACK, "  ")));
		VerificationResult missing = verifier.verify(STACK, List.of(), List.of(add(STACK, null)));

		assertThat(blank.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).contains(NoteCheckFailure.EVIDENCE_NOT_IN_MESSAGE));
		assertThat(missing.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).contains(NoteCheckFailure.EVIDENCE_NOT_IN_MESSAGE));
	}

	@Test
	void 근거는_공백과_문장부호가_달라도_같은_구절로_본다() {
		VerificationResult result = verifier.verify("스택은,   나중에 넣은 것이 먼저 나오는 구조이다!", List.of(),
			List.of(add(STACK, "스택은 나중에 넣은 것이 먼저 나오는 구조이다")));

		assertThat(result.accepted()).hasSize(1);
	}

	@Test
	void 학생이_말하지_않은_개념을_추가하면_탈락한다() {
		VerificationResult result = verifier.verify(STACK, List.of(),
			List.of(add("이진 탐색 트리는 왼쪽 자식이 부모보다 작은 값을 가진다", "스택은 나중에 넣은 것이")));

		assertThat(result.accepted()).isEmpty();
		assertThat(result.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).containsExactly(NoteCheckFailure.LOW_OVERLAP));
	}

	@Test
	void 부정을_뒤집어_옮기면_탈락한다() {
		String flipped = "스택은 나중에 넣은 것이 먼저 나오지 않는 구조이다";

		VerificationResult result = verifier.verify(STACK, List.of(), List.of(add(flipped, STACK)));

		assertThat(result.accepted()).isEmpty();
		assertThat(result.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).containsExactly(NoteCheckFailure.NEGATION_MISMATCH));
	}

	@Test
	void 학생이_부정으로_말한_것을_긍정으로_옮겨도_탈락한다() {
		String negative = "스택은 나중에 넣은 것이 먼저 나오지 않는 구조이다";

		VerificationResult result = verifier.verify(negative, List.of(), List.of(add(STACK, negative)));

		assertThat(result.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).contains(NoteCheckFailure.NEGATION_MISMATCH));
	}

	@Test
	void 학생이_부정으로_말한_것을_그대로_옮기면_통과한다() {
		String negative = "스택은 나중에 넣은 것이 먼저 나오지 않는 구조이다";

		VerificationResult result = verifier.verify(negative, List.of(), List.of(add(negative, negative)));

		assertThat(result.accepted()).hasSize(1);
	}

	@Test
	void 노트가_너무_짧거나_길면_탈락한다() {
		VerificationResult tooShort = verifier.verify("스택", List.of(), List.of(add("스택", "스택")));
		String longContent = "스택은 나중에 넣은 것이 먼저 나오는 구조이다. ".repeat(15);   // 300자 초과
		VerificationResult tooLong = verifier.verify(longContent, List.of(), List.of(add(longContent, STACK)));

		assertThat(tooShort.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).containsExactly(NoteCheckFailure.LENGTH_INVALID));
		assertThat(tooLong.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).contains(NoteCheckFailure.LENGTH_INVALID));
	}

	@Test
	void 내용이_없는_ADD는_탈락한다() {
		VerificationResult result = verifier.verify(STACK, List.of(), List.of(add(null, STACK)));

		assertThat(result.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).containsExactly(NoteCheckFailure.LENGTH_INVALID));
	}

	@Test
	void 앞뒤_공백은_잘라서_저장한다() {
		VerificationResult result = verifier.verify(STACK, List.of(), List.of(add("  " + STACK + "\n", " " + STACK + " ")));

		assertThat(result.accepted()).singleElement().isInstanceOfSatisfying(Add.class, added -> {
			assertThat(added.content()).isEqualTo(STACK);
			assertThat(added.evidence()).isEqualTo(STACK);
		});
	}

	// --- 유사 노트: 자동 병합 / 확인 카드 ---

	@Test
	void 유사도_0점9_이상이면_옛_노트를_자동_병합한다() {
		VerificationResult result = verifier.verify(STACK_NEAR_DUPLICATE, notes(1),
			List.of(add(STACK_NEAR_DUPLICATE, STACK_NEAR_DUPLICATE)));

		assertThat(result.rejected()).isEmpty();
		assertThat(result.accepted()).singleElement().isInstanceOfSatisfying(Add.class, added -> {
			assertThat(added.mergedNoteIds()).containsExactly(1L);
			assertThat(added.suggestions()).isEmpty();
		});
	}

	@Test
	void 유사도_0점75_이상_0점9_미만이면_합치기_확인을_제안한다() {
		VerificationResult result = verifier.verify(STACK_SIMILAR, notes(1), List.of(add(STACK_SIMILAR, STACK_SIMILAR)));

		assertThat(result.accepted()).singleElement().isInstanceOfSatisfying(Add.class, added -> {
			assertThat(added.mergedNoteIds()).isEmpty();
			assertThat(added.suggestions()).singleElement().satisfies(suggestion -> {
				assertThat(suggestion.noteId()).isEqualTo(1L);
				assertThat(suggestion.similarity()).isBetween(0.75, 0.9);
			});
		});
		assertThat(result.noteLimitReached()).isFalse();
	}

	@Test
	void 유사도_0점75_미만이면_병합도_제안도_없다() {
		VerificationResult result = verifier.verify(STACK_DIFFERENT_ENOUGH, notes(1),
			List.of(add(STACK_DIFFERENT_ENOUGH, STACK_DIFFERENT_ENOUGH)));

		assertThat(result.accepted()).singleElement().isInstanceOfSatisfying(Add.class, added -> {
			assertThat(added.mergedNoteIds()).isEmpty();
			assertThat(added.suggestions()).isEmpty();
		});
	}

	@Test
	void 비슷한_노트가_여럿이면_모두_병합하고_제안은_유사도_높은_순이다() {
		// 1번: STACK(0.909 병합), 2번: STACK_SIMILAR 에 가까운 노트(0.833 제안), 3번: 더 가까운 노트(제안, 더 높은 유사도)
		List<ActiveNote> existing = List.of(
			new ActiveNote(1, STACK),
			new ActiveNote(2, "스택은 나중에 넣은 것이 먼저 나오는 자료구조입니다"),
			new ActiveNote(3, "스택은 나중에 넣은 것이 먼저 나오는 구조입니다"));

		VerificationResult result = verifier.verify(STACK_NEAR_DUPLICATE, existing,
			List.of(add(STACK_NEAR_DUPLICATE, STACK_NEAR_DUPLICATE)));

		assertThat(result.accepted()).singleElement().isInstanceOfSatisfying(Add.class, added -> {
			assertThat(added.mergedNoteIds()).contains(1L);
			assertThat(added.suggestions()).isSortedAccordingTo(
				(a, b) -> Double.compare(b.similarity(), a.similarity()));
		});
	}

	// --- 상한 (60개) ---

	@Test
	void 노트가_59개면_ADD가_들어가고_60개가_된다() {
		VerificationResult result = verifier.verify(QUEUE, notes(59), List.of(add(QUEUE, QUEUE)));

		assertThat(result.accepted()).hasSize(1);
		assertThat(result.noteLimitReached()).isFalse();
	}

	@Test
	void 노트가_60개_차_있으면_병합이_아닌_ADD는_거부된다() {
		VerificationResult result = verifier.verify(QUEUE, notes(60), List.of(add(QUEUE, QUEUE)));

		assertThat(result.accepted()).isEmpty();
		assertThat(result.noteLimitReached()).isTrue();
		assertThat(result.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).containsExactly(NoteCheckFailure.NOTE_LIMIT_REACHED));
	}

	@Test
	void 노트가_60개_차_있어도_유사도_0점9_이상_병합은_통과한다() {
		VerificationResult result = verifier.verify(STACK_NEAR_DUPLICATE, notes(60),
			List.of(add(STACK_NEAR_DUPLICATE, STACK_NEAR_DUPLICATE)));

		assertThat(result.rejected()).isEmpty();
		assertThat(result.noteLimitReached()).isFalse();
		assertThat(result.accepted()).singleElement().isInstanceOfSatisfying(Add.class,
			added -> assertThat(added.mergedNoteIds()).containsExactly(1L));
	}

	@Test
	void 노트가_60개_차_있으면_확인_카드_구간의_ADD도_거부되고_제안도_없다() {
		VerificationResult result = verifier.verify(STACK_SIMILAR, notes(60), List.of(add(STACK_SIMILAR, STACK_SIMILAR)));

		assertThat(result.accepted()).isEmpty();
		assertThat(result.noteLimitReached()).isTrue();
		assertThat(result.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).containsExactly(NoteCheckFailure.NOTE_LIMIT_REACHED));
	}

	@Test
	void 노트가_60개_차_있어도_수정과_삭제는_된다() {
		VerificationResult result = verifier.verify("아무 말", notes(60),
			List.of(update(2L, "고쳐 쓴 노트 문장입니다"), remove(3L)));

		assertThat(result.rejected()).isEmpty();
		assertThat(result.accepted()).hasSize(2);
		assertThat(result.noteLimitReached()).isFalse();
	}

	@Test
	void 한_턴_안에서_삭제로_자리가_나면_뒤의_ADD가_들어간다() {
		VerificationResult result = verifier.verify(QUEUE, notes(60), List.of(remove(2L), add(QUEUE, QUEUE)));

		assertThat(result.rejected()).isEmpty();
		assertThat(result.accepted()).hasSize(2);
	}

	@Test
	void 한_턴_안에서_ADD가_먼저면_자리가_없어_거부되고_뒤의_삭제는_통과한다() {
		VerificationResult result = verifier.verify(QUEUE, notes(60), List.of(add(QUEUE, QUEUE), remove(2L)));

		assertThat(result.noteLimitReached()).isTrue();
		assertThat(result.accepted()).singleElement().isInstanceOf(Remove.class);
		assertThat(result.rejected()).hasSize(1);
	}

	@Test
	void 한_턴에_ADD를_여러_개_내도_상한을_넘지_않는다() {
		String message = QUEUE + ". " + "해시 테이블은 키로 값을 바로 찾는 구조이다";
		VerificationResult result = verifier.verify(message, notes(59),
			List.of(add(QUEUE, QUEUE), add("해시 테이블은 키로 값을 바로 찾는 구조이다", "해시 테이블은 키로 값을 바로 찾는 구조이다")));

		assertThat(result.accepted()).hasSize(1);
		assertThat(result.rejected()).hasSize(1);
		assertThat(result.noteLimitReached()).isTrue();
	}

	// --- UPDATE / REMOVE ---

	@Test
	void UPDATE는_근거_없이도_대상이_있고_길이가_맞으면_통과한다() {
		VerificationResult result = verifier.verify("아까 말한 건 틀렸어", notes(3), List.of(update(2L, "고쳐 쓴 노트 문장입니다")));

		assertThat(result.accepted()).singleElement().isInstanceOfSatisfying(Update.class, updated -> {
			assertThat(updated.targetId()).isEqualTo(2L);
			assertThat(updated.content()).isEqualTo("고쳐 쓴 노트 문장입니다");
		});
	}

	@Test
	void UPDATE_대상이_없거나_남의_노트면_탈락한다() {
		VerificationResult unknown = verifier.verify("고칠게", notes(3), List.of(update(999L, "고쳐 쓴 노트 문장입니다")));
		VerificationResult noTarget = verifier.verify("고칠게", notes(3), List.of(update(null, "고쳐 쓴 노트 문장입니다")));

		assertThat(unknown.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).containsExactly(NoteCheckFailure.TARGET_NOT_FOUND));
		assertThat(noTarget.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).containsExactly(NoteCheckFailure.TARGET_NOT_FOUND));
	}

	@Test
	void UPDATE_새_내용이_너무_짧으면_탈락하고_사유가_모두_남는다() {
		VerificationResult result = verifier.verify("고칠게", notes(3), List.of(update(999L, "짧음")));

		assertThat(result.rejected()).singleElement().satisfies(rejected -> assertThat(rejected.failedChecks())
			.containsExactly(NoteCheckFailure.TARGET_NOT_FOUND, NoteCheckFailure.LENGTH_INVALID));
	}

	@Test
	void REMOVE는_대상이_있으면_통과하고_없으면_탈락한다() {
		VerificationResult result = verifier.verify("그건 취소할게", notes(3), List.of(remove(2L), remove(999L), remove(null)));

		assertThat(result.accepted()).singleElement().isInstanceOfSatisfying(Remove.class,
			removed -> assertThat(removed.targetId()).isEqualTo(2L));
		assertThat(result.rejected()).hasSize(2)
			.allSatisfy(rejected -> assertThat(rejected.failedChecks()).containsExactly(NoteCheckFailure.TARGET_NOT_FOUND));
	}

	@Test
	void 한_턴에_같은_노트를_두_번_건드리면_두_번째는_탈락한다() {
		VerificationResult afterRemove = verifier.verify("취소", notes(3), List.of(remove(2L), update(2L, "고쳐 쓴 노트 문장입니다")));
		VerificationResult afterUpdate = verifier.verify("고침", notes(3),
			List.of(update(2L, "고쳐 쓴 노트 문장입니다"), remove(2L)));

		assertThat(afterRemove.accepted()).hasSize(1);
		assertThat(afterRemove.rejected()).singleElement()
			.satisfies(rejected -> assertThat(rejected.failedChecks()).containsExactly(NoteCheckFailure.TARGET_NOT_FOUND));
		assertThat(afterUpdate.accepted()).hasSize(1);
		assertThat(afterUpdate.rejected()).hasSize(1);
	}

	// --- 그 밖의 경계 ---

	@Test
	void 같은_턴에_거의_같은_ADD를_두_번_내면_뒤의_것은_탈락_기록_없이_버린다() {
		VerificationResult result = verifier.verify(STACK_NEAR_DUPLICATE + ". " + STACK, List.of(),
			List.of(add(STACK_NEAR_DUPLICATE, STACK_NEAR_DUPLICATE), add(STACK, STACK)));

		assertThat(result.accepted()).hasSize(1);
		assertThat(result.rejected()).isEmpty();
	}

	@Test
	void op가_없는_변경은_반영하지_않고_탈락으로_남긴다() {
		ProposedNoteChange noOp = new ProposedNoteChange(null, null, STACK, STACK);

		VerificationResult result = verifier.verify(STACK, List.of(), List.of(noOp));

		assertThat(result.accepted()).isEmpty();
		assertThat(result.rejected()).singleElement().satisfies(rejected -> {
			assertThat(rejected.change()).isSameAs(noOp);
			assertThat(rejected.failedChecks()).isEmpty();
		});
	}

	@Test
	void 변경은_AI가_준_순서대로_처리하고_통과와_탈락을_따로_모은다() {
		ProposedNoteChange bad = add(STACK, "학생이 하지 않은 말");
		ProposedNoteChange good = add(QUEUE, QUEUE);
		ProposedNoteChange gone = remove(999L);

		VerificationResult result = verifier.verify(STACK + ". " + QUEUE, List.of(), List.of(bad, good, gone));

		assertThat(result.accepted()).hasSize(1);
		assertThat(result.rejected()).extracting(RejectedChange::change).containsExactly(bad, gone);
	}

	@Test
	void 변경이_없으면_빈_결과다() {
		VerificationResult result = verifier.verify("안녕", List.of(), List.of());

		assertThat(result.accepted()).isEmpty();
		assertThat(result.rejected()).isEmpty();
		assertThat(result.noteLimitReached()).isFalse();
	}

}
