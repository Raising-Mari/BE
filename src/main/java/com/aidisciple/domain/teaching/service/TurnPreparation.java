package com.aidisciple.domain.teaching.service;

import com.aidisciple.domain.teaching.dto.TeachingTurnResponse;

/**
 * T-01 의 읽기 단계 결과. 같은 clientMessageId 가 이미 처리됐으면 {@code replay} 에 저장된 응답이 있고 AI 를 부르지 않는다.
 */
public record TurnPreparation(TurnInput input, long discipleId, long unitId, TeachingTurnResponse replay) {

	static TurnPreparation replayOf(TeachingTurnResponse response) {
		return new TurnPreparation(null, 0, 0, response);
	}

	boolean isReplay() {
		return replay != null;
	}

}
