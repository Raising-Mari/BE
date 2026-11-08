package com.aidisciple.domain.question.entity;

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

/** 학생이 랭킹 배틀에서 본 문항. 같은 문항은 재대결에서 빠진다. 연습 배틀은 기록하지 않는다. */
@Getter
@Entity
@Table(name = "user_question_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserQuestionHistory extends BaseCreatedEntity {

	/** DB source 컬럼의 값. 연습 배틀은 쓰지 않으므로 지금은 이 값 하나뿐이다. */
	private static final String SOURCE_BATTLE = "BATTLE";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long questionId;

	@Column(nullable = false, length = 10)
	private String source;

	private Long firstSeenBattleId;

	public static UserQuestionHistory seenInBattle(Long userId, Long questionId, Long battleId) {
		UserQuestionHistory history = new UserQuestionHistory();
		history.userId = userId;
		history.questionId = questionId;
		history.source = SOURCE_BATTLE;
		history.firstSeenBattleId = battleId;
		return history;
	}

}
