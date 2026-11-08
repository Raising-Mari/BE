package com.aidisciple.domain.auth.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aidisciple.domain.auth.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByGoogleSub(String googleSub);

	boolean existsByGoogleSub(String googleSub);

	/** 닉네임·이메일 UNIQUE 는 대소문자를 구분하지 않는 정렬 규칙을 쓰므로 "Abc" 와 "abc" 는 같은 닉네임이다. */
	boolean existsByNickname(String nickname);

	Optional<User> findByNickname(String nickname);

	/** 같은 사용자의 요청을 한 줄로 세워야 할 때(진행 중 배틀은 하나뿐) 쓰는 잠금 조회 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findLockedById(@Param("id") Long id);

}
