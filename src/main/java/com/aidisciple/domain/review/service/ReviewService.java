package com.aidisciple.domain.review.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.battle.dto.ReviewAnswerRef;
import com.aidisciple.domain.battle.dto.ReviewableBattle;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.battle.service.BattleReviewService;
import com.aidisciple.domain.question.dto.ReviewQuestion;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.domain.question.service.QuestionLookupService;
import com.aidisciple.domain.review.dto.CreateReportRequest;
import com.aidisciple.domain.review.dto.ReportResponse;
import com.aidisciple.domain.review.dto.RevealAnswersResponse;
import com.aidisciple.domain.review.dto.ReviewEntryResponse;
import com.aidisciple.domain.review.dto.ReviewResponse;
import com.aidisciple.domain.review.dto.SaveReviewRequest;
import com.aidisciple.domain.review.entity.Report;
import com.aidisciple.domain.review.entity.ReportKind;
import com.aidisciple.domain.review.entity.ReviewEntry;
import com.aidisciple.domain.review.repository.ReportRepository;
import com.aidisciple.domain.review.repository.ReviewEntryRepository;
import com.aidisciple.domain.teaching.dto.NoteRef;
import com.aidisciple.domain.teaching.service.NoteLookupService;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 복기(R-01·R-02)·신고(R-03)·정답 보기(R-11). 배틀은 {@link BattleReviewService} 로만 읽고 쓴다.
 * <b>정답 보기 전에는 모범 답과 자료 근거를 응답에 넣지 않는다</b>(null → JSON 에서 필드가 빠진다, api-spec 0절).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

	private final BattleReviewService battleReviewService;
	private final QuestionLookupService questionLookupService;
	private final QuestionBankService questionBankService;
	private final NoteLookupService noteLookupService;
	private final ReviewEntryRepository reviewEntryRepository;
	private final ReportRepository reportRepository;

	/** R-01: 끝난 RANKED 배틀의 내 답 전부(맞힌 문항 포함) */
	public ReviewResponse review(long userId, long battleId) {
		ReviewableBattle battle = battleReviewService.loadForReview(userId, battleId);
		boolean revealed = battle.answersRevealed();
		List<ReviewableBattle.Answer> answers = battle.answers();

		Map<Long, ReviewQuestion> questions = questionLookupService.reviewQuestions(
			answers.stream().map(ReviewableBattle.Answer::questionId).toList());
		Map<Long, ReviewEntry> myReviews = reviewEntryRepository.findByBattleAnswerIdIn(
				answers.stream().map(ReviewableBattle.Answer::battleAnswerId).toList()).stream()
			.collect(Collectors.toMap(ReviewEntry::getBattleAnswerId, Function.identity()));
		Map<Long, String> noteContents = noteLookupService.findByIds(
				answers.stream().flatMap(answer -> answer.citedNoteIds().stream()).distinct().toList()).stream()
			.collect(Collectors.toMap(NoteRef::id, NoteRef::content));

		List<ReviewResponse.Item> items = answers.stream()
			.map(answer -> toItem(answer, questions.get(answer.questionId()), myReviews.get(answer.battleAnswerId()),
				noteContents, revealed))
			.toList();
		return new ReviewResponse(revealed, items);
	}

	/** R-02: 원인·메모 저장. 이미 있으면 같은 줄을 고친다. 정답 보기 전에도 후에도 된다. */
	@Transactional
	public ReviewEntryResponse saveReview(long userId, long battleAnswerId, SaveReviewRequest request) {
		battleReviewService.requireOwnAnswer(userId, battleAnswerId);
		ReviewEntry entry = reviewEntryRepository.findByBattleAnswerId(battleAnswerId)
			.map(existing -> {
				existing.update(request.userCause(), blankToNull(request.studyMemo()), blankToNull(request.aiMemo()),
					blankToNull(request.sourceNote()), blankToNull(request.reasonNote()));
				return existing;
			})
			.orElseGet(() -> reviewEntryRepository.save(ReviewEntry.create(battleAnswerId, userId, request.userCause(),
				blankToNull(request.studyMemo()), blankToNull(request.aiMemo()), blankToNull(request.sourceNote()),
				blankToNull(request.reasonNote()))));
		return toEntryResponse(entry);
	}

	/**
	 * R-03: 문항 신고(QUESTION)는 그 문항을 바로 UNDER_REVIEW 로 바꿔 이후 배틀·연습에서 뺀다.
	 * 채점 이의제기(GRADING)는 접수만 한다. 같은 답에 같은 종류는 한 번만(REPORT_DUPLICATE).
	 */
	@Transactional
	public ReportResponse report(long userId, long battleAnswerId, CreateReportRequest request) {
		ReviewAnswerRef answer = battleReviewService.requireOwnAnswer(userId, battleAnswerId);
		if (reportRepository.existsByReporterIdAndBattleAnswerIdAndKind(userId, battleAnswerId, request.kind())) {
			throw new BusinessException(ErrorCode.REPORT_DUPLICATE);
		}
		Report report = reportRepository.saveAndFlush(Report.ofAnswer(userId, request.kind(), answer.questionId(),
			battleAnswerId, request.reasonType(), blankToNull(request.detail())));
		if (request.kind() == ReportKind.QUESTION) {
			questionBankService.markUnderReview(answer.questionId());
		}
		return new ReportResponse(report.getId(), report.getKind(), report.getStatus(), report.getCreatedAt());
	}

	/** R-11: 정답 보기. 되돌릴 수 없고 멱등이다(이미 열었으면 처음 연 시각). */
	@Transactional
	public RevealAnswersResponse revealAnswers(long userId, long battleId) {
		Instant revealedAt = battleReviewService.revealAnswers(userId, battleId);
		return new RevealAnswersResponse(true, revealedAt);
	}

	private ReviewResponse.Item toItem(ReviewableBattle.Answer answer, ReviewQuestion question, ReviewEntry myReview,
									   Map<Long, String> noteContents, boolean revealed) {
		ReviewResponse.Question questionView = new ReviewResponse.Question(answer.questionId(),
			question == null ? null : question.stem(),
			revealed && question != null ? question.referenceAnswer() : null);
		boolean injection = answer.injectionFlag() || answer.verdict() == Verdict.ZEROED_INJECTION;
		ReviewResponse.Disciple disciple = new ReviewResponse.Disciple(answer.answerText(), answer.unknown(),
			answer.verdict(), answer.finalScore(), answer.copyFactor(), injection);
		List<ReviewResponse.UsedNote> usedNotes = answer.citedNoteIds().stream()
			.filter(noteContents::containsKey)
			.map(id -> new ReviewResponse.UsedNote(id, noteContents.get(id)))
			.toList();
		// 맞힌 문항은 원인 제안이 필요 없다
		var causeSuggestion = answer.verdict() == Verdict.CORRECT ? null : answer.causeSuggestion();
		// 정답을 본 배틀은 연습을 시작할 수 없고, 신고·폐기된 문항은 다시 낼 수 없다
		boolean practiceAvailable = !revealed && question != null && question.practiceUsable();
		ReviewResponse.Evidence evidence = revealed && question != null && question.evidenceText() != null
			? new ReviewResponse.Evidence(question.evidenceText(), question.materialTitle()) : null;
		return new ReviewResponse.Item(answer.battleAnswerId(), answer.seq(), questionView, disciple, usedNotes,
			causeSuggestion, myReview == null ? null : toMyReview(myReview), practiceAvailable, evidence);
	}

	private static ReviewResponse.MyReview toMyReview(ReviewEntry entry) {
		return new ReviewResponse.MyReview(entry.getUserCause(), entry.getStudyMemo(), entry.getAiMemo(),
			entry.getSourceNote(), entry.getReasonNote());
	}

	private static ReviewEntryResponse toEntryResponse(ReviewEntry entry) {
		return new ReviewEntryResponse(entry.getBattleAnswerId(), entry.getUserCause(), entry.getStudyMemo(),
			entry.getAiMemo(), entry.getSourceNote(), entry.getReasonNote());
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
