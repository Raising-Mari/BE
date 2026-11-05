package com.aidisciple.domain.auth.entity;

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

/** 학과(전공). 가입 화면의 선택지이며 V1 에서 시드한다. 전공이 없는 학부는 학부 이름이 곧 {@code name} 이다. */
@Getter
@Entity
@Table(name = "departments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long schoolId;

	@Column(nullable = false, length = 100)
	private String collegeName;

	@Column(nullable = false, length = 100)
	private String name;

}
