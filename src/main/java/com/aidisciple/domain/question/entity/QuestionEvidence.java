package com.aidisciple.domain.question.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.aidisciple.global.jpa.BaseCreatedEntity;

/** 문항이 근거로 쓴 자료 문장. 자료 연결이 풀리면 이 표로 문항을 찾아 RETIRED 로 바꾼다. */
@Getter
@Entity
@Table(name = "question_evidences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionEvidence extends BaseCreatedEntity {

	@EmbeddedId
	private Key id;

	public static QuestionEvidence of(Long questionId, Long sentenceId) {
		QuestionEvidence evidence = new QuestionEvidence();
		evidence.id = new Key(questionId, sentenceId);
		return evidence;
	}

	@Getter
	@Embeddable
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@EqualsAndHashCode
	public static class Key implements Serializable {

		@Column(name = "question_id", nullable = false)
		private Long questionId;

		@Column(name = "sentence_id", nullable = false)
		private Long sentenceId;

		public Key(Long questionId, Long sentenceId) {
			this.questionId = questionId;
			this.sentenceId = sentenceId;
		}

	}

}
