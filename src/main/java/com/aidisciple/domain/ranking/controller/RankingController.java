package com.aidisciple.domain.ranking.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.ranking.dto.MyRatingResponse;
import com.aidisciple.domain.ranking.dto.OpponentRecordResponse;
import com.aidisciple.domain.ranking.dto.RankingResponse;
import com.aidisciple.domain.ranking.dto.RankingScope;
import com.aidisciple.global.api.NotImplemented;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

/** 8절 랭킹 (WP10). 랭킹·전적에는 RANKED 배틀만 센다. P1 인 K-04·K-05·K-07 은 이 스켈레톤에 넣지 않았다. */
@Tag(name = "랭킹")
@RestController
@RequestMapping("/api")
public class RankingController {

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
		return NotImplemented.yet();
	}

	@Operation(summary = "K-02 내 과목별 Elo")
	@GetMapping("/me/ratings")
	public List<MyRatingResponse> myRatings(@CurrentUser AuthUser user) {
		return NotImplemented.yet();
	}

	@Operation(summary = "K-03 상대별 전적")
	@GetMapping("/me/records")
	public List<OpponentRecordResponse> myRecords(
		@CurrentUser AuthUser user,
		@RequestParam(required = false) Long courseId
	) {
		return NotImplemented.yet();
	}

}
