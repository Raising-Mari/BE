package com.aidisciple.domain.material.entity;

/** DB {@code materials.status} 와 같은 값 (erd 3절) */
public enum MaterialStatus {
	PENDING,
	PROCESSING,
	DONE,
	FAILED,
	DELETED
}
