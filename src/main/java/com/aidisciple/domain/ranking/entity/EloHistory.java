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

import com.aidisciple.global.jpa.BaseCreatedEntity;

/**
 * 배틀 한 판이 사용자의 Elo 를 얼마나 바꿨나. 추가 전용이다. {@code UNIQUE(battle_id, user_id)} 가 같은 배틀을 두 번 반영하는 것을
 * 막는 마지막 보루다(멱등성). 성장폭 순위와 Elo 변동 이력은 이 테이블을 읽는다.
 */
@Getter
@Entity
@Table(name = "elo_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EloHistory extends BaseCreatedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long courseId;

	@Column(nullable = false)
	private Long battleId;

	@Column(nullable = false)
	private int ratingBefore;

	@Column(nullable = false)
	private int ratingAfter;

	@Column(nullable = false)
	private int delta;

	public static EloHistory of(Long userId, Long courseId, Long battleId, int ratingBefore, int ratingAfter) {
		EloHistory history = new EloHistory();
		history.userId = userId;
		history.courseId = courseId;
		history.battleId = battleId;
		history.ratingBefore = ratingBefore;
		history.ratingAfter = ratingAfter;
		history.delta = ratingAfter - ratingBefore;
		return history;
	}

}
