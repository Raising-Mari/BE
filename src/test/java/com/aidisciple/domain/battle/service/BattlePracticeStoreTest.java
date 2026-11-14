package com.aidisciple.domain.battle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.battle.dto.CreatePracticeRequest;
import com.aidisciple.domain.battle.dto.CreatePracticeResponse;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.battle.repository.BattleAnswerRepository;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.course.dto.DiscipleRef;
import com.aidisciple.domain.course.service.DiscipleService;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.domain.teaching.dto.NoteRef;
import com.aidisciple.domain.teaching.service.NoteLookupService;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class BattlePracticeStoreTest {

	private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
	private static final long ME = 10L;
	private static final long SOURCE_ID = 50L;
	private static final long PRACTICE_ID = 60L;
	private static final long MY_PARTICIPANT = 5L;

	private final BattleRepository battleRepository = mock(BattleRepository.class);
	private final BattleParticipantRepository participantRepository = mock(BattleParticipantRepository.class);
	private final BattleQuestionRepository questionRepository = mock(BattleQuestionRepository.class);
	private final BattleAnswerRepository answerRepository = mock(BattleAnswerRepository.class);
	private final UserLookupService userLookupService = mock(UserLookupService.class);
	private final DiscipleService discipleService = mock(DiscipleService.class);
	private final NoteLookupService noteLookupService = mock(NoteLookupService.class);
	private final QuestionBankService questionBankService = mock(QuestionBankService.class);
	private final BattlePracticeStore store = new BattlePracticeStore(battleRepository, participantRepository, questionRepository,
		answerRepository, userLookupService, discipleService, noteLookupService, questionBankService,
		Clock.fixed(NOW, ZoneOffset.UTC));

	private Battle source;
	private BattleParticipant sourceMe;

	@BeforeEach
	void 기본_상태() {
		source = Battle.openRoom(1L, 2L, 5, ME, "K7M4QX", NOW);
		ReflectionTestUtils.setField(source, "id", SOURCE_ID);
		ReflectionTestUtils.setField(source, "status", BattleStatus.FINISHED);
		sourceMe = BattleParticipant.joined(SOURCE_ID, ME, 100L, true, NOW);
		ReflectionTestUtils.setField(sourceMe, "id", MY_PARTICIPANT);
		when(battleRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
		when(participantRepository.findByBattleIdAndUserId(SOURCE_ID, ME)).thenReturn(Optional.of(sourceMe));
		when(discipleService.findByUserAndCourse(ME, 1L)).thenReturn(Optional.of(new DiscipleRef(100L, ME, 1L)));
		when(noteLookupService.activeNotes(100L, 2L)).thenReturn(List.of(new NoteRef(7L, "a"), new NoteRef(8L, "b")));
		// 원본 배틀 문항 5개: battle_question id 201~205, question id 901~905, seq 1~5
		when(questionRepository.findByBattleIdOrderBySeqAsc(SOURCE_ID)).thenReturn(List.of(
			sourceQuestion(201L, 901L, 1), sourceQuestion(202L, 902L, 2), sourceQuestion(203L, 903L, 3),
			sourceQuestion(204L, 904L, 4), sourceQuestion(205L, 905L, 5)));
		// 내 답: 1 정답, 2 오답, 3 모름, 4 부분, 5 지시문 의심 0점
		when(answerRepository.findByParticipantId(MY_PARTICIPANT)).thenReturn(List.of(
			answer(301L, 201L, Verdict.CORRECT), answer(302L, 202L, Verdict.WRONG), answer(303L, 203L, Verdict.UNKNOWN),
			answer(304L, 204L, Verdict.PARTIAL), answer(305L, 205L, Verdict.ZEROED_INJECTION)));
		when(questionBankService.practiceUsable(anyCollection())).thenAnswer(invocation -> Set.copyOf(invocation.<java.util.Collection<Long>>getArgument(0)));
		when(battleRepository.saveAndFlush(any(Battle.class))).thenAnswer(invocation -> {
			Battle saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", PRACTICE_ID);
			return saved;
		});
		when(participantRepository.save(any(BattleParticipant.class))).thenAnswer(invocation -> {
			BattleParticipant saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 6L);
			return saved;
		});
	}

	private static BattleQuestion sourceQuestion(long id, long questionId, int seq) {
		BattleQuestion question = BattleQuestion.of(SOURCE_ID, questionId, seq, null);
		ReflectionTestUtils.setField(question, "id", id);
		return question;
	}

	private static BattleAnswer answer(long id, long battleQuestionId, Verdict verdict) {
		BattleAnswer answer = BattleAnswer.solved(MY_PARTICIPANT, battleQuestionId, "답", false, "[7]");
		ReflectionTestUtils.setField(answer, "id", id);
		ReflectionTestUtils.setField(answer, "verdict", verdict);
		return answer;
	}

	private static ErrorCode codeOf(Runnable action) {
		Throwable thrown = catchThrowable(action::run);
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return ((BusinessException) thrown).getErrorCode();
	}

	private List<BattleQuestion> savedQuestions() {
		ArgumentCaptor<BattleQuestion> saved = ArgumentCaptor.forClass(BattleQuestion.class);
		verify(questionRepository, org.mockito.Mockito.atLeast(0)).save(saved.capture());
		return saved.getAllValues();
	}

	@Test
	void 생략하면_맞히지_못한_답의_같은_문항으로_연습_배틀이_RUNNING으로_시작한다() {
		BattlePracticeStore.Started started = store.create(ME, new CreatePracticeRequest(SOURCE_ID, null));

		CreatePracticeResponse response = started.response();
		assertThat(response.id()).isEqualTo(PRACTICE_ID);
		assertThat(response.mode()).isEqualTo(BattleMode.PRACTICE);
		assertThat(response.status()).isEqualTo(BattleStatus.RUNNING);
		assertThat(response.questionCount()).isEqualTo(3);   // 오답·모름·부분. 정답과 지시문 의심은 제외
		assertThat(response.skippedAnswerIds()).isEmpty();
		assertThat(started.participantId()).isEqualTo(6L);

		ArgumentCaptor<Battle> battle = ArgumentCaptor.forClass(Battle.class);
		verify(battleRepository).saveAndFlush(battle.capture());
		assertThat(battle.getValue().getMode()).isEqualTo(BattleMode.PRACTICE);
		assertThat(battle.getValue().getSourceBattleId()).isEqualTo(SOURCE_ID);
		assertThat(battle.getValue().getCourseId()).isEqualTo(1L);
		assertThat(battle.getValue().getUnitId()).isEqualTo(2L);
		assertThat(battle.getValue().getStartedAt()).isEqualTo(NOW);
		assertThat(battle.getValue().getRoomCode()).isNull();

		List<BattleQuestion> questions = savedQuestions();
		assertThat(questions).extracting(BattleQuestion::getQuestionId).containsExactly(902L, 903L, 904L);   // 같은 문항
		assertThat(questions).extracting(BattleQuestion::getSeq).containsExactly(1, 2, 3);
		assertThat(questions).extracting(BattleQuestion::getSourceAnswerId).containsExactly(302L, 303L, 304L);
		assertThat(questions).extracting(BattleQuestion::getBattleId).containsOnly(PRACTICE_ID);
	}

	@Test
	void 제자는_지금의_노트로_풀고_참가자는_만들_때_이미_준비된_상태다() {
		store.create(ME, new CreatePracticeRequest(SOURCE_ID, null));

		ArgumentCaptor<BattleParticipant> participant = ArgumentCaptor.forClass(BattleParticipant.class);
		verify(participantRepository).save(participant.capture());
		assertThat(participant.getValue().getBattleId()).isEqualTo(PRACTICE_ID);
		assertThat(participant.getValue().getUserId()).isEqualTo(ME);
		assertThat(participant.getValue().getDiscipleId()).isEqualTo(100L);
		assertThat(participant.getValue().isReady()).isTrue();
		assertThat(participant.getValue().getNoteIdsJson()).isEqualTo("[7,8]");
		assertThat(participant.getValue().getNoteCountSnapshot()).isEqualTo(2);
	}

	@Test
	void 노트가_0개여도_연습_배틀은_시작할_수_있다() {
		when(noteLookupService.activeNotes(100L, 2L)).thenReturn(List.of());

		BattlePracticeStore.Started started = store.create(ME, new CreatePracticeRequest(SOURCE_ID, null));

		assertThat(started.response().questionCount()).isEqualTo(3);
		ArgumentCaptor<BattleParticipant> participant = ArgumentCaptor.forClass(BattleParticipant.class);
		verify(participantRepository).save(participant.capture());
		assertThat(participant.getValue().getNoteIdsJson()).isEqualTo("[]");
		assertThat(participant.getValue().getNoteCountSnapshot()).isZero();
	}

	@Test
	void 답을_골라_주면_그_답들만_다시_내고_맞힌_답도_고를_수_있다() {
		BattlePracticeStore.Started started = store.create(ME, new CreatePracticeRequest(SOURCE_ID, List.of(304L, 301L, 304L)));

		assertThat(started.response().questionCount()).isEqualTo(2);   // 중복은 한 번만
		assertThat(savedQuestions()).extracting(BattleQuestion::getQuestionId).containsExactly(901L, 904L);   // 원본 문항 순서
		assertThat(savedQuestions()).extracting(BattleQuestion::getSourceAnswerId).containsExactly(301L, 304L);
	}

	@Test
	void 내_답이_아닌_id가_섞여_있으면_NOT_FOUND다() {
		assertThat(codeOf(() -> store.create(ME, new CreatePracticeRequest(SOURCE_ID, List.of(302L, 999L)))))
			.isEqualTo(ErrorCode.NOT_FOUND);
		verify(battleRepository, never()).saveAndFlush(any());
	}

	@Test
	void 신고되거나_폐기된_문항의_답은_건너뛰고_알려_준다() {
		when(questionBankService.practiceUsable(anyCollection())).thenReturn(Set.of(902L, 904L));   // 903 은 UNDER_REVIEW

		CreatePracticeResponse response = store.create(ME, new CreatePracticeRequest(SOURCE_ID, null)).response();

		assertThat(response.questionCount()).isEqualTo(2);
		assertThat(response.skippedAnswerIds()).containsExactly(303L);
		assertThat(savedQuestions()).extracting(BattleQuestion::getQuestionId).containsExactly(902L, 904L);
	}

	@Test
	void 쓸_수_있는_답이_하나도_없으면_PRACTICE_NOTHING_TO_REVIEW다() {
		when(questionBankService.practiceUsable(anyCollection())).thenReturn(Set.of());
		assertThat(codeOf(() -> store.create(ME, new CreatePracticeRequest(SOURCE_ID, null))))
			.isEqualTo(ErrorCode.PRACTICE_NOTHING_TO_REVIEW);

		assertThat(codeOf(() -> store.create(ME, new CreatePracticeRequest(SOURCE_ID, List.of()))))   // 아무것도 고르지 않음
			.isEqualTo(ErrorCode.PRACTICE_NOTHING_TO_REVIEW);
		verify(battleRepository, never()).saveAndFlush(any());
	}

	@Test
	void 전부_맞혔으면_다시_풀_답이_없다() {
		when(answerRepository.findByParticipantId(MY_PARTICIPANT)).thenReturn(List.of(
			answer(301L, 201L, Verdict.CORRECT), answer(302L, 202L, Verdict.CORRECT)));

		assertThat(codeOf(() -> store.create(ME, new CreatePracticeRequest(SOURCE_ID, null))))
			.isEqualTo(ErrorCode.PRACTICE_NOTHING_TO_REVIEW);
	}

	@Test
	void 연습_배틀은_원본으로_삼을_수_없다() {
		ReflectionTestUtils.setField(source, "mode", BattleMode.PRACTICE);

		assertThat(codeOf(() -> store.create(ME, new CreatePracticeRequest(SOURCE_ID, null)))).isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	void 끝나지_않은_배틀은_원본이_될_수_없다() {
		for (BattleStatus status : List.of(BattleStatus.LOBBY, BattleStatus.RUNNING, BattleStatus.ERROR, BattleStatus.CANCELED)) {
			ReflectionTestUtils.setField(source, "status", status);

			assertThat(codeOf(() -> store.create(ME, new CreatePracticeRequest(SOURCE_ID, null)))).isEqualTo(ErrorCode.BATTLE_NOT_FINISHED);
		}
	}

	@Test
	void 참가자가_아니거나_없는_배틀이면_NOT_FOUND다() {
		when(participantRepository.findByBattleIdAndUserId(SOURCE_ID, 99L)).thenReturn(Optional.empty());
		when(userLookupService.lockActive(99L)).thenReturn(null);
		when(battleRepository.findById(404L)).thenReturn(Optional.empty());

		assertThat(codeOf(() -> store.create(99L, new CreatePracticeRequest(SOURCE_ID, null)))).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(codeOf(() -> store.create(ME, new CreatePracticeRequest(404L, null)))).isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void 정답_보기를_누른_배틀은_연습을_시작할_수_없다() {
		ReflectionTestUtils.setField(sourceMe, "answersRevealedAt", NOW);

		assertThat(codeOf(() -> store.create(ME, new CreatePracticeRequest(SOURCE_ID, null))))
			.isEqualTo(ErrorCode.PRACTICE_ANSWERS_REVEALED);
		verify(battleRepository, never()).saveAndFlush(any());
	}

	@Test
	void 진행_중인_연습_배틀이_있으면_시작할_수_없다() {
		when(battleRepository.hasInProgress(ME, BattleMode.PRACTICE, NOW)).thenReturn(true);

		assertThat(codeOf(() -> store.create(ME, new CreatePracticeRequest(SOURCE_ID, null)))).isEqualTo(ErrorCode.BATTLE_ALREADY_ACTIVE);
	}

	@Test
	void 그_과목에_제자가_없으면_시작할_수_없다() {
		when(discipleService.findByUserAndCourse(ME, 1L)).thenReturn(Optional.empty());

		assertThat(codeOf(() -> store.create(ME, new CreatePracticeRequest(SOURCE_ID, null)))).isEqualTo(ErrorCode.BATTLE_DISCIPLE_REQUIRED);
	}

	@Test
	void 연습_배틀은_본_문항_기록을_남기지_않는다() {
		store.create(ME, new CreatePracticeRequest(SOURCE_ID, null));

		verify(questionBankService, never()).recordSeen(anyCollection(), anyCollection(), anyLong());
		verify(questionRepository, times(3)).save(any(BattleQuestion.class));
	}

}
