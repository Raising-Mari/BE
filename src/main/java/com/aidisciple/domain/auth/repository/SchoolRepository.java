package com.aidisciple.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.auth.entity.School;

public interface SchoolRepository extends JpaRepository<School, Long> {

	Optional<School> findByCode(String code);

	/** Google ID 토큰의 {@code hd} 로 학교를 찾는다. 컬럼 정렬 규칙이 대소문자를 구분하지 않는다. */
	Optional<School> findByEmailDomain(String emailDomain);

}
