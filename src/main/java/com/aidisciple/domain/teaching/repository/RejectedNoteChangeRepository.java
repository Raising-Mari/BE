package com.aidisciple.domain.teaching.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.teaching.entity.RejectedNoteChange;

public interface RejectedNoteChangeRepository extends JpaRepository<RejectedNoteChange, Long> {

	long countByMessageId(Long messageId);

}
