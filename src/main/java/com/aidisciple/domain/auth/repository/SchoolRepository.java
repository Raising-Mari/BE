package com.aidisciple.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.auth.entity.School;

public interface SchoolRepository extends JpaRepository<School, Long> {

	Optional<School> findByCode(String code);

}
