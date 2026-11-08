package com.aidisciple.domain.question.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.question.entity.QuestionValidationLog;

public interface QuestionValidationLogRepository extends JpaRepository<QuestionValidationLog, Long> {
}
