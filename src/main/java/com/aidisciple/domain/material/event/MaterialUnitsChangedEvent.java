package com.aidisciple.domain.material.event;

import java.util.List;

/**
 * 자료와 주차의 연결이 바뀌었다(M-06). 문제 은행(WP7)이 받아서 처리한다.
 * <ul>
 *   <li>{@code addedUnitIds}: 새로 연결된 주차. 자료가 이미 DONE({@code materialDone})이면 문항 생성 작업(J-02, {@code UNIT_MAPPED})을 건다.
 *       아직 처리 중이면 처리가 끝날 때 {@link MaterialProcessedEvent} 로 건다.</li>
 *   <li>{@code removedUnitIds}: 연결이 풀린 주차. 그 주차에서 이 자료의 문장을 근거로 쓰던 문항을 {@code RETIRED} 로 바꾼다.</li>
 * </ul>
 * 트랜잭션 안에서 발행되므로 받는 쪽은 {@code @TransactionalEventListener(AFTER_COMMIT)} 를 쓴다.
 */
public record MaterialUnitsChangedEvent(
	long materialId,
	long courseId,
	boolean materialDone,
	List<Long> addedUnitIds,
	List<Long> removedUnitIds
) {
}
