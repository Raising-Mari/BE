package com.aidisciple.domain.review.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.battle.dto.NoteExchangeRef;
import com.aidisciple.domain.battle.service.BattleReviewService;
import com.aidisciple.domain.review.dto.NoteReviewResponse;
import com.aidisciple.domain.review.dto.NoteReviewsResponse;
import com.aidisciple.domain.review.dto.OpponentNotesResponse;
import com.aidisciple.domain.review.dto.WriteNoteReviewRequest;
import com.aidisciple.domain.review.entity.NoteReview;
import com.aidisciple.domain.review.repository.NoteReviewRepository;
import com.aidisciple.domain.teaching.dto.NoteRef;
import com.aidisciple.domain.teaching.service.NoteLookupService;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 노트 교환 리뷰 R-06·R-07·R-08. 끝난 RANKED 배틀의 참가자만 쓸 수 있다(아니면 NOT_FOUND).
 * 상대 노트 보기와 리뷰 쓰기는 <b>두 사람 모두 공개에 동의</b>했을 때만 된다(REVIEW_NOTES_NOT_PUBLIC).
 * 노트는 상대가 준비 완료를 누른 순간의 스냅샷만 — 그 뒤에 고친 노트는 보이지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoteReviewService {

	private final BattleReviewService battleReviewService;
	private final NoteLookupService noteLookupService;
	private final UserLookupService userLookupService;
	private final NoteReviewRepository noteReviewRepository;

	/** R-06 */
	public OpponentNotesResponse opponentNotes(long userId, long battleId) {
		NoteExchangeRef exchange = requireBothPublic(userId, battleId);
		Map<Long, String> contents = noteContents(exchange.opponentNoteIds());
		List<OpponentNotesResponse.Note> notes = new ArrayList<>();
		for (Long noteId : exchange.opponentNoteIds()) {
			String content = contents.get(noteId);
			if (content != null) {
				notes.add(new OpponentNotesResponse.Note(noteId, content));
			}
		}
		String nickname = userLookupService.nicknames(List.of(exchange.opponentUserId())).get(exchange.opponentUserId());
		return new OpponentNotesResponse(battleId,
			new OpponentNotesResponse.Opponent(exchange.opponentUserId(), nickname), notes);
	}

	/** R-07: 배틀당 한 번. 근거 노트는 상대의 스냅샷 노트 중에서만. */
	@Transactional
	public NoteReviewResponse write(long userId, long battleId, WriteNoteReviewRequest request) {
		NoteExchangeRef exchange = requireBothPublic(userId, battleId);
		if (noteReviewRepository.existsByBattleIdAndReviewerId(battleId, userId)) {
			throw new BusinessException(ErrorCode.REVIEW_ALREADY_SUBMITTED);
		}
		Long evidenceId = request.evidenceNoteEntryId();
		if (evidenceId != null && !exchange.opponentNoteIds().contains(evidenceId)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "근거 노트는 상대가 이 배틀에 쓴 노트 중에서 골라야 합니다.");
		}
		NoteReview saved = noteReviewRepository.saveAndFlush(NoteReview.write(battleId, userId,
			exchange.opponentUserId(), request.goodPoints().strip(), request.suggestions().strip(), evidenceId));
		return toResponse(saved);
	}

	/** R-08: 공개 동의가 없어도 부를 수 있다(그때는 둘 다 null). */
	public NoteReviewsResponse list(long userId, long battleId) {
		NoteExchangeRef exchange = battleReviewService.loadNoteExchange(userId, battleId);
		NoteReviewResponse written = noteReviewRepository.findByBattleIdAndReviewerId(battleId, userId)
			.map(this::toResponse).orElse(null);
		NoteReviewResponse received = noteReviewRepository.findByBattleIdAndReviewerId(battleId, exchange.opponentUserId())
			.map(this::toResponse).orElse(null);
		return new NoteReviewsResponse(written, received);
	}

	private NoteExchangeRef requireBothPublic(long userId, long battleId) {
		NoteExchangeRef exchange = battleReviewService.loadNoteExchange(userId, battleId);
		if (!exchange.bothPublic()) {
			throw new BusinessException(ErrorCode.REVIEW_NOTES_NOT_PUBLIC);
		}
		return exchange;
	}

	private NoteReviewResponse toResponse(NoteReview review) {
		Map<Long, String> nicknames = userLookupService.nicknames(List.of(review.getReviewerId(), review.getTargetUserId()));
		NoteReviewResponse.EvidenceNote evidence = null;
		if (review.getEvidenceNoteEntryId() != null) {
			String content = noteContents(List.of(review.getEvidenceNoteEntryId())).get(review.getEvidenceNoteEntryId());
			if (content != null) {
				evidence = new NoteReviewResponse.EvidenceNote(review.getEvidenceNoteEntryId(), content);
			}
		}
		return new NoteReviewResponse(review.getId(), review.getBattleId(),
			new NoteReviewResponse.Person(review.getReviewerId(), nicknames.get(review.getReviewerId())),
			new NoteReviewResponse.Person(review.getTargetUserId(), nicknames.get(review.getTargetUserId())),
			review.getGoodPoints(), review.getSuggestions(), evidence, review.getCreatedAt());
	}

	private Map<Long, String> noteContents(List<Long> noteIds) {
		if (noteIds.isEmpty()) {
			return Map.of();
		}
		return noteLookupService.findByIds(noteIds).stream()
			.collect(Collectors.toMap(NoteRef::id, NoteRef::content, (first, second) -> first));
	}

}
