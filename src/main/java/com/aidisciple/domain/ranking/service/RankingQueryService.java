package com.aidisciple.domain.ranking.service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.aidisciple.global.api.Pages;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 랭킹·전적 조회(K-01~K-05, K-07). 랭킹 배틀(RANKED)만 센다 — 연습 배틀 기록은 B-06 에서 본다.
 * 순위는 {@code games_played ≥ 1} 인 사람만, 레이팅이 같으면 user_id 가 작은 쪽이 앞이다. 순위 조회는 페이지로만 내려준다.
 * 과목은 같은 학교 것만 볼 수 있다(없는 과목과 다른 학교의 과목은 같은 NOT_FOUND).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingQueryService {

	private static final int MIN_GAMES = 1;
	static final int DEFAULT_GROWTH_DAYS = 21;
	static final int MAX_GROWTH_DAYS = 365;

	private final EloRatingRepository ratingRepository;
	private final EloHistoryRepository historyRepository;
	private final BattleRecordService battleRecordService;
	private final UserLookupService userLookupService;
	private final DepartmentService departmentService;
	private final CourseService courseService;
	private final Clock clock;

	// --- K-01 과목별 순위 ---

	public RankingResponse rankings(long userId, long courseId, RankingScope scope, Long departmentId, Integer page, Integer size) {
		UserRef user = userLookupService.requireActive(userId);
		courseService.requireVisible(courseId, user);
		Pageable pageable = Pages.of(page, size, Sort.by(Sort.Order.desc("rating"), Sort.Order.asc("userId")));

		// null 이면 학교 전체, 아니면 그 학과 학생들
		List<Long> memberIds = null;
		if (scope == RankingScope.DEPARTMENT) {
			long scopeDepartmentId = scopeDepartment(user, departmentId);
			memberIds = userLookupService.activeUserIdsInDepartment(scopeDepartmentId);
		}
		Page<EloRating> found = memberIds == null
			? ratingRepository.findByCourseIdAndGamesPlayedGreaterThanEqual(courseId, MIN_GAMES, pageable)
			: memberIds.isEmpty() ? Page.empty(pageable)
			: ratingRepository.findByCourseIdAndGamesPlayedGreaterThanEqualAndUserIdIn(courseId, MIN_GAMES, memberIds, pageable);

		Set<Long> userIds = found.getContent().stream().map(EloRating::getUserId).collect(Collectors.toCollection(HashSet::new));
		userIds.add(userId);
		Map<Long, UserSummary> users = userLookupService.summaries(userIds);
		Map<Long, String> departmentNames = departmentNames(users.values());

		int offset = found.getNumber() * found.getSize();
		List<RankingResponse.Entry> items = new ArrayList<>();
		for (int i = 0; i < found.getContent().size(); i++) {
			EloRating rating = found.getContent().get(i);
			items.add(toEntry(offset + i + 1, rating, users.get(rating.getUserId()), departmentNames));
		}
		RankingResponse.Entry me = myEntry(userId, courseId, memberIds, users.get(userId), departmentNames);
		return new RankingResponse(items, me, found.getNumber(), found.getSize(), found.getTotalElements(), found.hasNext());
	}

	/** 학과 범위: {@code departmentId} 를 안 주면 내 학과. 같은 학교의 학과만 고를 수 있다. */
	private long scopeDepartment(UserRef user, Long departmentId) {
		Long target = departmentId != null ? departmentId : user.departmentId();
		if (target == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "학과를 지정해 주세요.");
		}
		departmentService.findInSchool(target, user.schoolId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		return target;
	}

	/** 내 순위. 배틀을 한 적이 없거나(범위가 학과면) 그 학과 학생이 아니면 null. */
	private RankingResponse.Entry myEntry(long userId, long courseId, List<Long> memberIds, UserSummary summary,
										  Map<Long, String> departmentNames) {
		Optional<EloRating> mine = ratingRepository.findByUserIdAndCourseId(userId, courseId)
			.filter(rating -> rating.getGamesPlayed() >= MIN_GAMES);
		if (mine.isEmpty() || (memberIds != null && !memberIds.contains(userId))) {
			return null;
		}
		EloRating rating = mine.get();
		long ahead = memberIds == null
			? ratingRepository.countByCourseIdAndGamesPlayedGreaterThanEqualAndRatingGreaterThan(courseId, MIN_GAMES, rating.getRating())
				+ ratingRepository.countByCourseIdAndGamesPlayedGreaterThanEqualAndRatingAndUserIdLessThan(courseId, MIN_GAMES,
					rating.getRating(), userId)
			: ratingRepository.countByCourseIdAndGamesPlayedGreaterThanEqualAndUserIdInAndRatingGreaterThan(courseId, MIN_GAMES,
				memberIds, rating.getRating())
				+ ratingRepository.countByCourseIdAndGamesPlayedGreaterThanEqualAndUserIdInAndRatingAndUserIdLessThan(courseId,
					MIN_GAMES, memberIds, rating.getRating(), userId);
		return toEntry((int) ahead + 1, rating, summary, departmentNames);
	}

	private static RankingResponse.Entry toEntry(int rank, EloRating rating, UserSummary summary, Map<Long, String> departmentNames) {
		return new RankingResponse.Entry(rank, rating.getUserId(), nicknameOf(summary), departmentNameOf(summary, departmentNames),
			rating.getRating(), rating.getGamesPlayed(), rating.getWins(), rating.getLosses(), rating.getDraws());
	}

	// --- K-02 내 과목별 Elo ---

	public List<MyRatingResponse> myRatings(long userId) {
		userLookupService.requireActive(userId);
		List<EloRating> ratings = ratingRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
			.filter(rating -> rating.getGamesPlayed() >= MIN_GAMES)
			.toList();
		Map<Long, String> courseNames = courseService.findNames(ratings.stream().map(EloRating::getCourseId).distinct().toList());
		return ratings.stream()
			.map(rating -> new MyRatingResponse(new MyRatingResponse.Course(rating.getCourseId(), courseNames.get(rating.getCourseId())),
				rating.getRating(), rating.getGamesPlayed(), rating.getWins(), rating.getLosses(), rating.getDraws()))
			.toList();
	}

	// --- K-03 상대별 전적, K-04 상대와의 배틀 목록 ---

	/** 상대별 승·패·무와 마지막으로 겨룬 때. 가장 최근에 겨룬 상대가 먼저 온다. */
	public List<OpponentRecordResponse> records(long userId, Long courseId) {
		userLookupService.requireActive(userId);
		List<RankedBattleRecord> records = battleRecordService.rankedRecordsOf(userId, courseId);   // 최근 것부터
		Map<Long, List<RankedBattleRecord>> byOpponent = records.stream()
			.collect(Collectors.groupingBy(RankedBattleRecord::opponentId, LinkedHashMap::new, Collectors.toList()));
		Map<Long, String> nicknames = userLookupService.nicknames(byOpponent.keySet());
		return byOpponent.entrySet().stream()
			.map(entry -> new OpponentRecordResponse(
				new OpponentRecordResponse.Opponent(entry.getKey(), nicknames.getOrDefault(entry.getKey(), "")),
				count(entry.getValue(), BattleResult.WIN), count(entry.getValue(), BattleResult.LOSE),
				count(entry.getValue(), BattleResult.DRAW), entry.getValue().get(0).finishedAt()))
			.sorted(Comparator.comparing(OpponentRecordResponse::lastBattleAt, Comparator.nullsLast(Comparator.reverseOrder())))
			.toList();
	}

	/** 한 상대와 한 랭킹 배틀을 최근 것부터. 상대와 한 적이 없으면 빈 페이지다(그 사용자가 있는지 알려 주지 않는다). */
	public PageResponse<OpponentBattleResponse> opponentBattles(long userId, long opponentId, Long courseId, Integer page, Integer size) {
		userLookupService.requireActive(userId);
		Pageable pageable = Pages.of(page, size);
		List<RankedBattleRecord> all = battleRecordService.rankedRecordsOf(userId, courseId).stream()
			.filter(record -> record.opponentId() == opponentId)
			.toList();
		int from = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), all.size());
		int to = Math.min(all.size(), from + pageable.getPageSize());
		List<RankedBattleRecord> slice = all.subList(from, to);
		Map<Long, String> courseNames = courseService.findNames(slice.stream().map(RankedBattleRecord::courseId).distinct().toList());
		List<OpponentBattleResponse> items = slice.stream()
			.map(record -> new OpponentBattleResponse(record.battleId(),
				new OpponentBattleResponse.Course(record.courseId(), courseNames.get(record.courseId())), record.result(),
				record.myScore(), record.opponentScore(), record.finishedAt()))
			.toList();
		return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(), all.size(), to < all.size());
	}

	private static int count(List<RankedBattleRecord> records, BattleResult result) {
		return (int) records.stream().filter(record -> record.result() == result).count();
	}

	// --- K-05 성장폭 순위 ---

	/**
	 * 기간({@code days}, 기본 21일) 안의 Elo 증감 합 기준 순위. 그 기간에 랭킹 배틀이 없는 사람은 나오지 않는다.
	 * 한 과목의 기간 안 변동 이력을 읽어 합치므로 학교 규모에서만 쓴다(그 정도면 수백 건이다).
	 */
	public GrowthRankingResponse growth(long userId, long courseId, Integer days, Integer page, Integer size) {
		UserRef user = userLookupService.requireActive(userId);
		courseService.requireVisible(courseId, user);
		int window = days == null ? DEFAULT_GROWTH_DAYS : days;
		if (window < 1 || window > MAX_GROWTH_DAYS) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "days 는 1 이상 " + MAX_GROWTH_DAYS + " 이하여야 합니다.");
		}
		Pageable pageable = Pages.of(page, size);

		Map<Long, Integer> deltaByUser = new LinkedHashMap<>();
		for (EloHistory history : historyRepository.findByCourseIdAndCreatedAtGreaterThanEqual(courseId,
			clock.instant().minus(Duration.ofDays(window)))) {
			deltaByUser.merge(history.getUserId(), history.getDelta(), Integer::sum);
		}
		List<Map.Entry<Long, Integer>> ranked = deltaByUser.entrySet().stream()
			.sorted(Map.Entry.<Long, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
			.toList();

		int from = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), ranked.size());
		int to = Math.min(ranked.size(), from + pageable.getPageSize());
		List<Map.Entry<Long, Integer>> slice = ranked.subList(from, to);

		Set<Long> userIds = slice.stream().map(Map.Entry::getKey).collect(Collectors.toCollection(HashSet::new));
		userIds.add(userId);
		Map<Long, UserSummary> users = userLookupService.summaries(userIds);
		Map<Long, String> departmentNames = departmentNames(users.values());
		Map<Long, EloRating> ratings = ratingRepository.findByCourseIdAndUserIdIn(courseId, userIds).stream()
			.collect(Collectors.toMap(EloRating::getUserId, rating -> rating));

		List<GrowthRankingResponse.Entry> items = new ArrayList<>();
		for (int i = 0; i < slice.size(); i++) {
			Map.Entry<Long, Integer> row = slice.get(i);
			items.add(toGrowthEntry(from + i + 1, row.getKey(), row.getValue(), ratings.get(row.getKey()), users.get(row.getKey()),
				departmentNames));
		}
		GrowthRankingResponse.Entry me = null;
		for (int i = 0; i < ranked.size(); i++) {
			if (ranked.get(i).getKey() == userId) {
				me = toGrowthEntry(i + 1, userId, ranked.get(i).getValue(), ratings.get(userId), users.get(userId), departmentNames);
				break;
			}
		}
		return new GrowthRankingResponse(items, me, pageable.getPageNumber(), pageable.getPageSize(), ranked.size(), to < ranked.size());
	}

	private static GrowthRankingResponse.Entry toGrowthEntry(int rank, long userId, int delta, EloRating rating, UserSummary summary,
															 Map<Long, String> departmentNames) {
		return new GrowthRankingResponse.Entry(rank, userId, nicknameOf(summary), departmentNameOf(summary, departmentNames),
			rating == null ? 0 : rating.getRating(), rating == null ? 0 : rating.getGamesPlayed(),
			rating == null ? 0 : rating.getWins(), rating == null ? 0 : rating.getLosses(), rating == null ? 0 : rating.getDraws(), delta);
	}

	// --- K-07 내 Elo 변동 이력 ---

	/** 최신 것부터. {@code courseId} 를 주면 그 과목만. 상대는 그 배틀의 다른 참가자다. */
	public PageResponse<EloHistoryResponse> history(long userId, Long courseId, Integer page, Integer size) {
		userLookupService.requireActive(userId);
		Pageable pageable = Pages.of(page, size);
		Page<EloHistory> found = courseId == null
			? historyRepository.findByUserIdOrderByIdDesc(userId, pageable)
			: historyRepository.findByUserIdAndCourseIdOrderByIdDesc(userId, courseId, pageable);
		Map<Long, Long> opponentIds = battleRecordService.opponentIds(userId, found.getContent().stream().map(EloHistory::getBattleId).toList());
		Map<Long, String> nicknames = userLookupService.nicknames(new HashSet<>(opponentIds.values()));
		List<EloHistoryResponse> items = found.getContent().stream()
			.map(history -> {
				Long opponentId = opponentIds.get(history.getBattleId());
				return new EloHistoryResponse(history.getBattleId(),
					opponentId == null ? null : new EloHistoryResponse.Opponent(opponentId, nicknames.getOrDefault(opponentId, "")),
					history.getRatingBefore(), history.getRatingAfter(), history.getDelta(), history.getCreatedAt());
			})
			.toList();
		return new PageResponse<>(items, found.getNumber(), found.getSize(), found.getTotalElements(), found.hasNext());
	}

	// --- 공통 ---

	private Map<Long, String> departmentNames(Collection<UserSummary> users) {
		List<Long> ids = users.stream().map(UserSummary::departmentId).filter(java.util.Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return departmentService.findByIds(ids).values().stream()
			.collect(Collectors.toMap(DepartmentResponse::id, DepartmentResponse::name));
	}

	private static String nicknameOf(UserSummary summary) {
		return summary == null ? "" : summary.nickname();
	}

	/** 학과가 없는 사용자는 null */
	private static String departmentNameOf(UserSummary summary, Map<Long, String> departmentNames) {
		return summary == null || summary.departmentId() == null ? null : departmentNames.get(summary.departmentId());
	}

}
