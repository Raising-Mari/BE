package com.aidisciple.domain.battle.dto;

import com.aidisciple.domain.battle.entity.Verdict;

/** 복기 저장(R-02)·신고(R-03)가 "내 답인가"를 확인한 결과. 신고는 이 문항 id 로 문항을 UNDER_REVIEW 로 바꾼다. */
public record ReviewAnswerRef(long battleAnswerId, long questionId, Verdict verdict) {
}
