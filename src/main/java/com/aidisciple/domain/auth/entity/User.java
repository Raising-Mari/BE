package com.aidisciple.domain.auth.entity;

import java.time.Instant;
import java.util.Locale;

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
import com.aidisciple.global.security.UserRole;

/**
 * 학교 Google 계정으로 가입한 사용자. 학교·학과는 가입 기능(WP3)에서 연관 엔티티로 바꿀 때까지 id 로만 가진다.
 * enum 은 DB 가 VARCHAR 이므로 네이티브 ENUM 타입으로 추정되지 않게 JDBC 타입을 고정한다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long schoolId;

	private Long departmentId;

	@Column(nullable = false)
	private String googleSub;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false, length = 20)
	private String nickname;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@Column(nullable = false)
	private Instant termsAgreedAt;

	private Instant lastLoginAt;

	public static User create(Long schoolId, Long departmentId, String googleSub, String email, String nickname,
		UserRole role, Instant termsAgreedAt) {
		User user = new User();
		user.schoolId = schoolId;
		user.departmentId = departmentId;
		user.googleSub = googleSub;
		user.email = email.toLowerCase(Locale.ROOT);
		user.nickname = nickname;
		user.role = role;
		user.status = UserStatus.ACTIVE;
		user.termsAgreedAt = termsAgreedAt;
		return user;
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

	public void markLoggedIn(Instant now) {
		this.lastLoginAt = now;
	}

}
