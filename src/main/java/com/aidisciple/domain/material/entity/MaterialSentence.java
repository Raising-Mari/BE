package com.aidisciple.domain.material.entity;

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
 * 자료에서 뽑은 문장. 근거 문장 확인·복붙 보정·문항 생성이 모두 이 테이블을 읽는다.
 * 저장은 건수가 많아 {@code MaterialSentenceWriter} 가 JDBC 배치로 하고, 이 엔티티는 읽기와 스키마 검증용이다.
 * 문장 내용은 어떤 학생용 응답에도 담지 않는다.
 */
@Getter
@Entity
@Table(name = "material_sentences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialSentence extends BaseCreatedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long materialId;

	@Column(nullable = false)
	private int seq;

	private Integer pageNo;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String text;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String normalizedText;

	@Column(nullable = false, length = 64, columnDefinition = "CHAR(64)")
	private String textHash;

}
