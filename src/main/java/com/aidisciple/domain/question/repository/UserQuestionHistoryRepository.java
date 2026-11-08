package com.aidisciple.domain.question.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.question.entity.UserQuestionHistory;

public interface UserQuestionHistoryRepository extends JpaRepository<UserQuestionHistory, Long> {
}
