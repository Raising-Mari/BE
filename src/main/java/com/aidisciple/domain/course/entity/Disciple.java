package com.aidisciple.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.aidisciple.global.jpa.BaseTimeEntity;

/**
 * 제자. 학생 한 명이 과목마다 한 명을 가지며, 제자를 만드는 것이 곧 그 과목의 수강 등록이다.
 * 수강 등록과 한 몸이라 과목 도메인이 소유한다. 가르치기·배틀은 {@code DiscipleService} 로만 읽는다.
 */
@Getter
@Entity
@Table(name = "disciples")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Disciple extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long courseId;

	@Column(nullable = false, length = 30)
	private String name;

	@Column(length = 30)
	private String characterKey;

	public static Disciple create(Long userId, Long courseId, String name, String characterKey) {
		Disciple disciple = new Disciple();
		disciple.userId = userId;
		disciple.courseId = courseId;
		disciple.name = name;
		disciple.characterKey = characterKey;
		return disciple;
	}

}
