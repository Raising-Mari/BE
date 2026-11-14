package com.aidisciple.domain.material.entity;

/** DB {@code materials.fail_reason} 와 같은 값 (erd 3절). {@code NO_TEXT} 는 스캔 PDF 처럼 글자를 뽑지 못한 경우다. */
public enum MaterialFailReason {
	NO_TEXT,
	TOO_LARGE,
	PARSE_ERROR,
	SERVER_RESTART
}
