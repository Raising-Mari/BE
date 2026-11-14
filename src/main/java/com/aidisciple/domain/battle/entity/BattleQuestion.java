package com.aidisciple.domain.battle.entity;

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
 * 배틀에 고정된 문항. 시작 시점에 한 번 정해 두 참가자가 같은 세트를 푼다.
 * 연습 배틀에서는 {@code sourceAnswerId} 가 이 문항이 다시 시험하는 원본 배틀의 답이다.
 */
@Getter
@Entity
@Table(name = "battle_questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BattleQuestion extends BaseCreatedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long battleId;

	@Column(nullable = false)
	private Long questionId;

	@Column(nullable = false, columnDefinition = "TINYINT")
	private int seq;

	private Long sourceAnswerId;

	public static BattleQuestion of(Long battleId, Long questionId, int seq, Long sourceAnswerId) {
		BattleQuestion question = new BattleQuestion();
		question.battleId = battleId;
		question.questionId = questionId;
		question.seq = seq;
		question.sourceAnswerId = sourceAnswerId;
		return question;
	}

}
