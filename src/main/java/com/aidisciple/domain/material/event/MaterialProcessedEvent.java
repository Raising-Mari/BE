package com.aidisciple.domain.material.event;

import java.util.List;

/**
 * 자료 처리가 끝나 문장이 저장됐다(DONE). {@code unitIds} 는 그 시점에 이미 연결돼 있던 주차다.
 * 문제 은행(WP7)이 이 주차마다 문항 생성 작업(J-02, {@code MATERIAL_DONE})을 건다. 트랜잭션 안에서 발행되므로
 * 받는 쪽은 {@code @TransactionalEventListener(AFTER_COMMIT)} 로 받아야 저장된 문장을 읽을 수 있다.
 */
public record MaterialProcessedEvent(long materialId, long courseId, List<Long> unitIds) {
}
