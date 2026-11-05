package com.aidisciple.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.auth.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByGoogleSub(String googleSub);

	boolean existsByGoogleSub(String googleSub);

	/** 닉네임·이메일 UNIQUE 는 대소문자를 구분하지 않는 정렬 규칙을 쓰므로 "Abc" 와 "abc" 는 같은 닉네임이다. */
	boolean existsByNickname(String nickname);

	Optional<User> findByNickname(String nickname);

}
