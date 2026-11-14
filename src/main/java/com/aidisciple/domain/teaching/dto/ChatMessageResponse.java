package com.aidisciple.domain.teaching.dto;

import java.time.Instant;

import com.aidisciple.domain.teaching.entity.ChatRole;

/** T-02 대화 내역의 항목 */
public record ChatMessageResponse(
	Long id,
	ChatRole role,
	Long unitId,
	int turnNo,
	String content,
	boolean askedWhy,
	Instant createdAt
) {
}
