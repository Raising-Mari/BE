package com.aidisciple.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aidisciple.global.jpa.BaseTimeEntity;

/**
 * 과목. 학교 안에서 (정규화한 이름, 학기)가 유일하다. 같은 과목을 학생마다 따로 만들면 친구와 겨룰 수 없기 때문이다.
 * 학교·학과·만든 사람은 다른 도메인의 엔티티라서 id 로만 가진다.
 */
@Getter
@Entity
@Table(name = "courses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long schoolId;

	private Long departmentId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 100)
	private String normalizedName;

	@Column(nullable = false, length = 10)
	private String term;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private CourseStatus status;

	@Column(nullable = false)
	private Long createdBy;

	public static Course create(Long schoolId, Long departmentId, String name, String normalizedName, String term, Long createdBy) {
		Course course = new Course();
		course.schoolId = schoolId;
		course.departmentId = departmentId;
		course.name = name;
		course.normalizedName = normalizedName;
		course.term = term;
		course.status = CourseStatus.ACTIVE;
		course.createdBy = createdBy;
		return course;
	}

}
