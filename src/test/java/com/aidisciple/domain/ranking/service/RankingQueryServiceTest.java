package com.aidisciple.domain.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.auth.dto.DepartmentResponse;
import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.dto.UserSummary;
import com.aidisciple.domain.auth.service.DepartmentService;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.battle.dto.RankedBattleRecord;
import com.aidisciple.domain.battle.entity.BattleResult;
import com.aidisciple.domain.battle.service.BattleRecordService;
import com.aidisciple.domain.course.service.CourseService;
import com.aidisciple.domain.ranking.dto.EloHistoryResponse;
import com.aidisciple.domain.ranking.dto.GrowthRankingResponse;
import com.aidisciple.domain.ranking.dto.MyRatingResponse;
import com.aidisciple.domain.ranking.dto.OpponentBattleResponse;
import com.aidisciple.domain.ranking.dto.OpponentRecordResponse;
import com.aidisciple.domain.ranking.dto.RankingResponse;
import com.aidisciple.domain.ranking.dto.RankingScope;
import com.aidisciple.domain.ranking.entity.EloHistory;
import com.aidisciple.domain.ranking.entity.EloRating;
import com.aidisciple.domain.ranking.repository.EloHistoryRepository;
import com.aidisciple.domain.ranking.repository.EloRatingRepository;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class RankingQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
	private static final long ME = 10L;
	private static final long COURSE_ID = 1L;
	private static final long DEPARTMENT_ID = 7L;

	private final EloRatingRepository ratingRepository = mock(EloRatingRepository.class);
	private final EloHistoryRepository historyRepository = mock(EloHistoryRepository.class);
	private final BattleRecordService battleRecordService = mock(BattleRecordService.class);
	private final UserLookupService userLookupService = mock(UserLookupService.class);
	private final DepartmentService departmentService = mock(DepartmentService.class);
	private final CourseService courseService = mock(CourseService.class);
	private final RankingQueryService service = new RankingQueryService(ratingRepository, historyRepository, battleRecordService,
		userLookupService, departmentService, courseService, Clock.fixed(NOW, ZoneOffset.UTC));

	private final UserRef me = new UserRef(ME, 1L, DEPARTMENT_ID);

	@BeforeEach
	void 기본_상태() {
		when(userLookupService.requireActive(ME)).thenReturn(me);
		when(userLookupService.summaries(anyCollection())).thenAnswer(invocation -> {
			java.util.Collection<Long> ids = invocation.getArgument(0);
			return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id,
				id -> new UserSummary(id, "닉" + id, id == 12L ? null : DEPARTMENT_ID)));
		});
		when(userLookupService.nicknames(anyCollection())).thenAnswer(invocation -> {
			java.util.Collection<Long> ids = invocation.getArgument(0);
			return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> "닉" + id));
		});
		when(departmentService.findByIds(anyCollection())).thenReturn(Map.of(DEPARTMENT_ID,
			new DepartmentResponse(DEPARTMENT_ID, "컴퓨터공학과", "ICT융합대학")));
		when(departmentService.findInSchool(DEPARTMENT_ID, 1L)).thenReturn(Optional.of(
			new DepartmentResponse(DEPARTMENT_ID, "컴퓨터공학과", "ICT융합대학")));
		when(courseService.findNames(anyCollection())).thenReturn(Map.of(COURSE_ID, "자료구조", 2L, "운영체제"));
	}

	private static EloRating rating(long userId, long courseId, int rating, int wins, int losses, int draws) {
		EloRating row = EloRating.initial(userId, courseId);
		ReflectionTestUtils.setField(row, "rating", rating);
		ReflectionTestUtils.setField(row, "gamesPlayed", wins + losses + draws);
		ReflectionTestUtils.setField(row, "wins", wins);
		ReflectionTestUtils.setField(row, "losses", losses);
		ReflectionTestUtils.setField(row, "draws", draws);
		ReflectionTestUtils.setField(row, "updatedAt", NOW);
		return row;
	}

	private static ErrorCode codeOf(Runnable action) {
		Throwable thrown = catchThrowable(action::run);
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return ((BusinessException) thrown).getErrorCode();
	}

	// --- K-01 ---

	@Test
	void 학교_순위는_레이팅_내림차순_같으면_user_id_오름차순으로_페이지를_준다() {
		when(ratingRepository.findByCourseIdAndGamesPlayedGreaterThanEqual(eq(COURSE_ID), eq(1), any(Pageable.class)))
			.thenAnswer(invocation -> new PageImpl<>(List.of(rating(12L, COURSE_ID, 1100, 2, 1, 0), rating(13L, COURSE_ID, 1050, 1, 1, 1)),
				invocation.<Pageable>getArgument(2), 6));
		when(ratingRepository.findByUserIdAndCourseId(ME, COURSE_ID)).thenReturn(Optional.empty());

		RankingResponse response = service.rankings(ME, COURSE_ID, RankingScope.SCHOOL, null, 1, 2);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(ratingRepository).findByCourseIdAndGamesPlayedGreaterThanEqual(eq(COURSE_ID), eq(1), pageable.capture());
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("rating"), Sort.Order.asc("userId")));
		assertThat(response.items()).extracting(RankingResponse.Entry::rank).containsExactly(3, 4);   // 1페이지(0부터) × 2 + 1
		assertThat(response.items().get(0).userId()).isEqualTo(12L);
		assertThat(response.items().get(0).nickname()).isEqualTo("닉12");
		assertThat(response.items().get(0).departmentName()).isNull();   // 학과 없는 사용자
		assertThat(response.items().get(1).departmentName()).isEqualTo("컴퓨터공학과");
		assertThat(response.items().get(1)).extracting(RankingResponse.Entry::rating, RankingResponse.Entry::gamesPlayed,
			RankingResponse.Entry::wins, RankingResponse.Entry::losses, RankingResponse.Entry::draws).containsExactly(1050, 3, 1, 1, 1);
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(6);
		assertThat(response.hasNext()).isTrue();
		assertThat(response.me()).isNull();   // 배틀 전이면 null
	}

	@Test
	void 내_순위는_나보다_앞선_사람_수에_1을_더한다() {
		when(ratingRepository.findByCourseIdAndGamesPlayedGreaterThanEqual(eq(COURSE_ID), eq(1), any(Pageable.class)))
			.thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.<Pageable>getArgument(2), 0));
		when(ratingRepository.findByUserIdAndCourseId(ME, COURSE_ID)).thenReturn(Optional.of(rating(ME, COURSE_ID, 1020, 3, 1, 0)));
		when(ratingRepository.countByCourseIdAndGamesPlayedGreaterThanEqualAndRatingGreaterThan(COURSE_ID, 1, 1020)).thenReturn(4L);
		when(ratingRepository.countByCourseIdAndGamesPlayedGreaterThanEqualAndRatingAndUserIdLessThan(COURSE_ID, 1, 1020, ME)).thenReturn(1L);

		RankingResponse response = service.rankings(ME, COURSE_ID, RankingScope.SCHOOL, null, null, null);

		assertThat(response.me().rank()).isEqualTo(6);   // 레이팅이 높은 4명 + 같은 레이팅에 id 가 작은 1명 + 1
		assertThat(response.me().userId()).isEqualTo(ME);
		assertThat(response.me().rating()).isEqualTo(1020);
		assertThat(response.me().departmentName()).isEqualTo("컴퓨터공학과");
		assertThat(response.items()).isEmpty();
	}

	@Test
	void 학과_순위는_기본이_내_학과이고_그_학과_학생만_센다() {
		when(userLookupService.activeUserIdsInDepartment(DEPARTMENT_ID)).thenReturn(List.of(ME, 13L));
		when(ratingRepository.findByCourseIdAndGamesPlayedGreaterThanEqualAndUserIdIn(eq(COURSE_ID), eq(1), eq(List.of(ME, 13L)),
			any(Pageable.class))).thenAnswer(invocation -> new PageImpl<>(List.of(rating(13L, COURSE_ID, 1080, 2, 0, 0),
				rating(ME, COURSE_ID, 1020, 1, 1, 0)), invocation.<Pageable>getArgument(3), 2));
		when(ratingRepository.findByUserIdAndCourseId(ME, COURSE_ID)).thenReturn(Optional.of(rating(ME, COURSE_ID, 1020, 1, 1, 0)));
		when(ratingRepository.countByCourseIdAndGamesPlayedGreaterThanEqualAndUserIdInAndRatingGreaterThan(COURSE_ID, 1,
			List.of(ME, 13L), 1020)).thenReturn(1L);

		RankingResponse response = service.rankings(ME, COURSE_ID, RankingScope.DEPARTMENT, null, null, null);

		assertThat(response.items()).extracting(RankingResponse.Entry::userId).containsExactly(13L, ME);
		assertThat(response.me().rank()).isEqualTo(2);
		verify(ratingRepository, never()).findByCourseIdAndGamesPlayedGreaterThanEqual(anyLong(), anyInt(), any());
	}

	@Test
	void 학과를_골라_주면_그_학과이고_내_학과가_아니면_나는_순위에_없다() {
		long other = 8L;
		when(departmentService.findInSchool(other, 1L)).thenReturn(Optional.of(new DepartmentResponse(other, "경영학과", "경영대학")));
		when(userLookupService.activeUserIdsInDepartment(other)).thenReturn(List.of(20L));
		when(ratingRepository.findByCourseIdAndGamesPlayedGreaterThanEqualAndUserIdIn(eq(COURSE_ID), eq(1), eq(List.of(20L)),
			any(Pageable.class))).thenAnswer(invocation -> new PageImpl<>(List.of(rating(20L, COURSE_ID, 1010, 1, 0, 0)),
				invocation.<Pageable>getArgument(3), 1));
		when(ratingRepository.findByUserIdAndCourseId(ME, COURSE_ID)).thenReturn(Optional.of(rating(ME, COURSE_ID, 1020, 1, 1, 0)));

		RankingResponse response = service.rankings(ME, COURSE_ID, RankingScope.DEPARTMENT, other, null, null);

		assertThat(response.items()).extracting(RankingResponse.Entry::userId).containsExactly(20L);
		assertThat(response.me()).isNull();
	}

	@Test
	void 학과에_학생이_없으면_빈_페이지다() {
		when(userLookupService.activeUserIdsInDepartment(DEPARTMENT_ID)).thenReturn(List.of());
		when(ratingRepository.findByUserIdAndCourseId(ME, COURSE_ID)).thenReturn(Optional.empty());

		RankingResponse response = service.rankings(ME, COURSE_ID, RankingScope.DEPARTMENT, null, null, null);

		assertThat(response.items()).isEmpty();
		assertThat(response.totalElements()).isZero();
		verify(ratingRepository, never()).findByCourseIdAndGamesPlayedGreaterThanEqualAndUserIdIn(anyLong(), anyInt(), anyCollection(), any());
	}

	@Test
	void 학과가_없는_사용자가_학과를_안_고르면_INVALID_INPUT이고_다른_학교_학과는_NOT_FOUND다() {
		UserRef noDepartment = new UserRef(11L, 1L, null);
		when(userLookupService.requireActive(11L)).thenReturn(noDepartment);
		when(departmentService.findInSchool(99L, 1L)).thenReturn(Optional.empty());

		assertThat(codeOf(() -> service.rankings(11L, COURSE_ID, RankingScope.DEPARTMENT, null, null, null)))
			.isEqualTo(ErrorCode.INVALID_INPUT);
		assertThat(codeOf(() -> service.rankings(ME, COURSE_ID, RankingScope.DEPARTMENT, 99L, null, null))).isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void 볼_수_없는_과목의_순위는_조회하지_않는다() {
		when(courseService.requireVisible(9L, me)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

		assertThat(codeOf(() -> service.rankings(ME, 9L, RankingScope.SCHOOL, null, null, null))).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(codeOf(() -> service.growth(ME, 9L, null, null, null))).isEqualTo(ErrorCode.NOT_FOUND);
		verify(ratingRepository, never()).findByCourseIdAndGamesPlayedGreaterThanEqual(anyLong(), anyInt(), any());
	}

	// --- K-02 ---

	@Test
	void 내_Elo는_과목별로_이름과_전적을_준다() {
		when(ratingRepository.findByUserIdOrderByUpdatedAtDesc(ME)).thenReturn(List.of(
			rating(ME, 2L, 984, 0, 1, 0), rating(ME, COURSE_ID, 1016, 1, 0, 0)));

		List<MyRatingResponse> ratings = service.myRatings(ME);

		assertThat(ratings).extracting(r -> r.course().name()).containsExactly("운영체제", "자료구조");
		assertThat(ratings.get(1)).isEqualTo(new MyRatingResponse(new MyRatingResponse.Course(COURSE_ID, "자료구조"), 1016, 1, 1, 0, 0));
	}

	// --- K-03·K-04 ---

	private static RankedBattleRecord record(long battleId, long opponentId, BattleResult result, String finishedAt) {
		return new RankedBattleRecord(battleId, COURSE_ID, Instant.parse(finishedAt), result, new BigDecimal("1.00"), opponentId,
			new BigDecimal("0.50"));
	}

	@Test
	void 상대별_전적은_승패무를_세고_가장_최근에_겨룬_상대가_먼저다() {
		when(battleRecordService.rankedRecordsOf(ME, null)).thenReturn(List.of(   // 최근 것부터
			record(5L, 11L, BattleResult.WIN, "2026-11-08T00:00:00Z"),
			record(4L, 12L, BattleResult.LOSE, "2026-11-07T00:00:00Z"),
			record(3L, 11L, BattleResult.LOSE, "2026-11-06T00:00:00Z"),
			record(2L, 11L, BattleResult.DRAW, "2026-11-05T00:00:00Z"),
			record(1L, 12L, BattleResult.WIN, "2026-11-04T00:00:00Z")));

		List<OpponentRecordResponse> records = service.records(ME, null);

		assertThat(records).extracting(r -> r.opponent().id()).containsExactly(11L, 12L);
		assertThat(records.get(0)).isEqualTo(new OpponentRecordResponse(new OpponentRecordResponse.Opponent(11L, "닉11"), 1, 1, 1,
			Instant.parse("2026-11-08T00:00:00Z")));
		assertThat(records.get(1)).extracting(OpponentRecordResponse::wins, OpponentRecordResponse::losses, OpponentRecordResponse::draws,
			OpponentRecordResponse::lastBattleAt).containsExactly(1, 1, 0, Instant.parse("2026-11-07T00:00:00Z"));
	}

	@Test
	void 전적이_없으면_빈_목록이고_과목_조건은_그대로_넘긴다() {
		when(battleRecordService.rankedRecordsOf(ME, COURSE_ID)).thenReturn(List.of());

		assertThat(service.records(ME, COURSE_ID)).isEmpty();
		verify(battleRecordService).rankedRecordsOf(ME, COURSE_ID);
	}

	@Test
	void 상대와의_배틀_목록은_그_상대만_최근순으로_페이지로_준다() {
		when(battleRecordService.rankedRecordsOf(ME, null)).thenReturn(List.of(
			record(5L, 11L, BattleResult.WIN, "2026-11-08T00:00:00Z"),
			record(4L, 12L, BattleResult.LOSE, "2026-11-07T00:00:00Z"),
			record(3L, 11L, BattleResult.LOSE, "2026-11-06T00:00:00Z"),
			record(2L, 11L, BattleResult.DRAW, "2026-11-05T00:00:00Z")));

		PageResponse<OpponentBattleResponse> first = service.opponentBattles(ME, 11L, null, 0, 2);
		PageResponse<OpponentBattleResponse> second = service.opponentBattles(ME, 11L, null, 1, 2);

		assertThat(first.items()).extracting(OpponentBattleResponse::battleId).containsExactly(5L, 3L);
		assertThat(first.totalElements()).isEqualTo(3);
		assertThat(first.hasNext()).isTrue();
		assertThat(first.items().get(0).course().name()).isEqualTo("자료구조");
		assertThat(first.items().get(0).result()).isEqualTo(BattleResult.WIN);
		assertThat(second.items()).extracting(OpponentBattleResponse::battleId).containsExactly(2L);
		assertThat(second.hasNext()).isFalse();
	}

	@Test
	void 한_적_없는_상대와의_배틀_목록은_빈_페이지다() {
		when(battleRecordService.rankedRecordsOf(ME, null)).thenReturn(List.of(record(5L, 11L, BattleResult.WIN, "2026-11-08T00:00:00Z")));

		PageResponse<OpponentBattleResponse> page = service.opponentBattles(ME, 999L, null, 5, 20);   // 범위를 벗어난 페이지도 안전하다

		assertThat(page.items()).isEmpty();
		assertThat(page.totalElements()).isZero();
		assertThat(page.hasNext()).isFalse();
	}

	// --- K-05 ---

	private static EloHistory history(long userId, long battleId, int delta) {
		return EloHistory.of(userId, COURSE_ID, battleId, 1000, 1000 + delta);
	}

	@Test
	void 성장폭_순위는_기간_안의_증감_합이_큰_순서이고_동점이면_user_id가_작은_쪽이_앞이다() {
		when(historyRepository.findByCourseIdAndCreatedAtGreaterThanEqual(COURSE_ID, NOW.minusSeconds(21L * 24 * 3600))).thenReturn(List.of(
			history(11L, 1L, 16), history(12L, 1L, -16), history(11L, 2L, 10), history(13L, 3L, 26), history(ME, 4L, -5), history(12L, 5L, 26)));
		when(ratingRepository.findByCourseIdAndUserIdIn(eq(COURSE_ID), anyCollection())).thenReturn(List.of(
			rating(11L, COURSE_ID, 1026, 2, 0, 0), rating(13L, COURSE_ID, 1026, 1, 0, 0), rating(12L, COURSE_ID, 1010, 1, 1, 0)));

		GrowthRankingResponse response = service.growth(ME, COURSE_ID, null, null, 3);

		// 합: 11 → 26, 12 → 10, 13 → 26, 나 → -5. 26 동점은 id 가 작은 11 이 앞.
		assertThat(response.items()).extracting(GrowthRankingResponse.Entry::userId).containsExactly(11L, 13L, 12L);
		assertThat(response.items()).extracting(GrowthRankingResponse.Entry::rank).containsExactly(1, 2, 3);
		assertThat(response.items()).extracting(GrowthRankingResponse.Entry::delta).containsExactly(26, 26, 10);
		assertThat(response.items().get(0).rating()).isEqualTo(1026);
		assertThat(response.items().get(0).nickname()).isEqualTo("닉11");
		assertThat(response.totalElements()).isEqualTo(4);
		assertThat(response.hasNext()).isTrue();
		assertThat(response.me().rank()).isEqualTo(4);
		assertThat(response.me().delta()).isEqualTo(-5);
	}

	@Test
	void 기간에_배틀이_없으면_내_순위는_null이다() {
		when(historyRepository.findByCourseIdAndCreatedAtGreaterThanEqual(eq(COURSE_ID), any())).thenReturn(List.of(history(11L, 1L, 16)));
		when(ratingRepository.findByCourseIdAndUserIdIn(eq(COURSE_ID), anyCollection())).thenReturn(List.of());

		GrowthRankingResponse response = service.growth(ME, COURSE_ID, 7, null, null);

		assertThat(response.me()).isNull();
		assertThat(response.items()).hasSize(1);
		assertThat(response.items().get(0).rating()).isZero();   // 레이팅 행을 못 찾아도 깨지지 않는다
		verify(historyRepository).findByCourseIdAndCreatedAtGreaterThanEqual(COURSE_ID, NOW.minusSeconds(7L * 24 * 3600));
	}

	@Test
	void 기간은_1일_이상_365일_이하만_된다() {
		assertThat(codeOf(() -> service.growth(ME, COURSE_ID, 0, null, null))).isEqualTo(ErrorCode.INVALID_INPUT);
		assertThat(codeOf(() -> service.growth(ME, COURSE_ID, 366, null, null))).isEqualTo(ErrorCode.INVALID_INPUT);
		assertThat(codeOf(() -> service.growth(ME, COURSE_ID, -3, null, null))).isEqualTo(ErrorCode.INVALID_INPUT);
	}

	// --- K-07 ---

	@Test
	void Elo_변동_이력은_상대를_붙여_최신순으로_준다() {
		EloHistory latest = history(ME, 50L, 16);
		EloHistory older = history(ME, 40L, -8);
		when(historyRepository.findByUserIdOrderByIdDesc(eq(ME), any(Pageable.class))).thenAnswer(invocation ->
			new PageImpl<>(List.of(latest, older), invocation.<Pageable>getArgument(1), 2));
		when(battleRecordService.opponentIds(eq(ME), anyCollection())).thenReturn(Map.of(50L, 11L, 40L, 12L));

		PageResponse<EloHistoryResponse> page = service.history(ME, null, null, null);

		assertThat(page.items()).extracting(EloHistoryResponse::battleId).containsExactly(50L, 40L);
		assertThat(page.items().get(0).opponent()).isEqualTo(new EloHistoryResponse.Opponent(11L, "닉11"));
		assertThat(page.items().get(0)).extracting(EloHistoryResponse::ratingBefore, EloHistoryResponse::ratingAfter,
			EloHistoryResponse::delta).containsExactly(1000, 1016, 16);
		assertThat(page.items().get(1).delta()).isEqualTo(-8);
		verify(historyRepository, never()).findByUserIdAndCourseIdOrderByIdDesc(anyLong(), anyLong(), any());
	}

	@Test
	void 과목을_주면_그_과목_이력만_보고_상대를_못_찾아도_깨지지_않는다() {
		EloHistory only = history(ME, 50L, 16);
		when(historyRepository.findByUserIdAndCourseIdOrderByIdDesc(eq(ME), eq(COURSE_ID), any(Pageable.class))).thenAnswer(invocation ->
			new PageImpl<>(List.of(only), invocation.<Pageable>getArgument(2), 1));
		when(battleRecordService.opponentIds(eq(ME), anyCollection())).thenReturn(Map.of());

		PageResponse<EloHistoryResponse> page = service.history(ME, COURSE_ID, null, null);

		assertThat(page.items()).hasSize(1);
		assertThat(page.items().get(0).opponent()).isNull();
		verify(historyRepository, never()).findByUserIdOrderByIdDesc(anyLong(), any());
	}

}
