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
 * 단원 = 주차. 과목 안에서 모든 학생이 공유한다(친구와 같은 주차로 배틀하기 위함). 정규화한 이름이 같으면 중복이다.
 * 학생 화면에서만 "주차"라고 부르고 코드와 DB 는 {@code unit} 이다.
 */
@Getter
@Entity
@Table(name = "units")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Unit extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long courseId;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 100)
	private String normalizedTitle;

	@Column(nullable = false)
	private Long createdBy;

	public static Unit create(Long courseId, String title, String normalizedTitle, Long createdBy) {
		Unit unit = new Unit();
		unit.courseId = courseId;
		unit.title = title;
		unit.normalizedTitle = normalizedTitle;
		unit.createdBy = createdBy;
		return unit;
	}

}
