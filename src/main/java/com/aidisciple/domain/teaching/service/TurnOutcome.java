package com.aidisciple.domain.teaching.service;

import com.aidisciple.domain.teaching.logic.VerificationResult;

/** 한 턴의 처리 결과. 이 값을 짧은 트랜잭션 안에서 저장하는 것은 서비스(6-B)의 몫이다. */
public record TurnOutcome(String reply, boolean askedWhy, VerificationResult verification) {
}
