package com.aidisciple.global.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 추가 전용(수정하지 않는) 테이블의 생성 시각. {@code updated_at} 이 없는 테이블이 상속한다. */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseCreatedEntity {

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

}
