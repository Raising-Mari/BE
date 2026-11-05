package com.aidisciple.domain.material.dto;

import java.util.List;

import com.aidisciple.domain.material.entity.MaterialFailReason;
import com.aidisciple.domain.material.entity.MaterialStatus;

/** M-03 처리 상태 + 연결된 주차. 문장 내용은 어떤 학생용 응답에도 담지 않는다. */
public record MaterialResponse(
	Long id,
	String title,
	MaterialStatus status,
	MaterialFailReason failReason,
	int sentenceCount,
	List<Long> unitIds
) {
}
