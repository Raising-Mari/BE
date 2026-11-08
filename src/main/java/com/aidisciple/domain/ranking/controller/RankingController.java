package com.aidisciple.domain.ranking.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.ranking.dto.EloHistoryResponse;
import com.aidisciple.domain.ranking.dto.GrowthRankingResponse;
import com.aidisciple.domain.ranking.dto.MyRatingResponse;
import com.aidisciple.domain.ranking.dto.OpponentBattleResponse;
import com.aidisciple.domain.ranking.dto.OpponentRecordResponse;
import com.aidisciple.domain.ranking.dto.RankingResponse;
import com.aidisciple.domain.ranking.dto.RankingScope;
import com.aidisciple.domain.ranking.service.RankingQueryService;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

/** 8절 랭킹. 랭킹·전적에는 RANKED 배틀만 센다. K-06(학과·단과대 대항전)은 P2 라 아직 없다. */
@Tag(name = "랭킹")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RankingController {

	private final RankingQueryService queryService;

	@Operation(summary = "K-01 과목별 순위")
	@GetMapping("/rankings")
	public RankingResponse rankings(
		@CurrentUser AuthUser user,
		@RequestParam Long courseId,
		@RequestParam(defaultValue = "SCHOOL") RankingScope scope,
		@RequestParam(required = false) Long departmentId,
		@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size
	) {
		return queryService.rankings(user.userId(), courseId, scope, departmentId, page, size);
	}

	@Operation(summary = "K-05 성장폭 순위 (기간 안의 Elo 증감 기준)")
	@GetMapping("/rankings/growth")
	public GrowthRankingResponse growth(
		@CurrentUser AuthUser user,
		@RequestParam Long courseId,
		@RequestParam(required = false) Integer days,
		@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size
	) {
		return queryService.growth(user.userId(), courseId, days, page, size);
	}

	@Operation(summary = "K-02 내 과목별 Elo")
	@GetMapping("/me/ratings")
	public List<MyRatingResponse> myRatings(@CurrentUser AuthUser user) {
		return queryService.myRatings(user.userId());
	}

	@Operation(summary = "K-07 내 Elo 변동 이력 (최신순)")
	@GetMapping("/me/ratings/history")
	public PageResponse<EloHistoryResponse> ratingHistory(
		@CurrentUser AuthUser user,
		@RequestParam(required = false) Long courseId,
		@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size
	) {
		return queryService.history(user.userId(), courseId, page, size);
	}

	@Operation(summary = "K-03 상대별 전적")
	@GetMapping("/me/records")
	public List<OpponentRecordResponse> myRecords(
		@CurrentUser AuthUser user,
		@RequestParam(required = false) Long courseId
	) {
		return queryService.records(user.userId(), courseId);
	}

	@Operation(summary = "K-04 특정 상대와 한 랭킹 배틀 목록")
	@GetMapping("/me/records/{opponentId}/battles")
	public PageResponse<OpponentBattleResponse> opponentBattles(
		@CurrentUser AuthUser user,
		@PathVariable Long opponentId,
		@RequestParam(required = false) Long courseId,
		@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size
	) {
		return queryService.opponentBattles(user.userId(), opponentId, courseId, page, size);
	}

}
