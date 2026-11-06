package com.aidisciple.domain.material.extract;

import com.aidisciple.domain.material.entity.MaterialFailReason;

/**
 * 자료 처리 중 예상 가능한 실패. {@code reason} 이 그대로 {@code materials.fail_reason} 이 된다.
 * 메시지에는 파일 내용·추출한 글을 담지 않는다. (로그에 남아도 되도록)
 */
public class MaterialProcessingException extends RuntimeException {

	private final MaterialFailReason reason;

	public MaterialProcessingException(MaterialFailReason reason) {
		super(reason.name());
		this.reason = reason;
	}

	public MaterialProcessingException(MaterialFailReason reason, Throwable cause) {
		super(reason.name(), cause);
		this.reason = reason;
	}

	public MaterialFailReason reason() {
		return reason;
	}

}
