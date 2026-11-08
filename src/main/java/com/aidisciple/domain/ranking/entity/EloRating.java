package com.aidisciple.domain.ranking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.aidisciple.domain.battle.entity.BattleResult;
import com.aidisciple.domain.ranking.logic.EloCalculator;
import com.aidisciple.global.jpa.BaseTimeEntity;

/**
 * 사용자의 과목별 Elo 와 전적. 사용자·과목마다 한 행이고(UNIQUE), 첫 랭킹 배틀이 끝날 때 만든다.
 * 갱신할 때는 {@code FOR UPDATE} 로 잠그고 읽는다 — 두 행을 항상 user_id 순으로 잠가 교착을 피한다.
 */
@Getter
@Entity
@Table(name = "elo_ratings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EloRating extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long courseId;

	@Column(nullable = false)
	private int rating;

	@Column(nullable = false)
	private int gamesPlayed;

	@Column(nullable = false)
	private int wins;

	@Column(nullable = false)
	private int losses;

	@Column(nullable = false)
	private int draws;

	/** 아직 배틀을 하지 않은 초기 상태(1000, 0전) */
	public static EloRating initial(Long userId, Long courseId) {
		EloRating rating = new EloRating();
		rating.userId = userId;
		rating.courseId = courseId;
		rating.rating = EloCalculator.INITIAL_RATING;
		return rating;
	}

	/** 배틀 한 판의 결과를 반영한다: 새 레이팅과 승·무·패 횟수, 경기 수 */
	public void applyResult(int newRating, BattleResult result) {
		this.rating = newRating;
		this.gamesPlayed++;
		switch (result) {
			case WIN -> this.wins++;
			case LOSE -> this.losses++;
			case DRAW -> this.draws++;
		}
	}

}
