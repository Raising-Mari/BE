package com.aidisciple.domain.teaching.logic;

import java.util.List;

/**
 * 한 턴의 노트 변경 검증 결과.
 * {@code noteLimitReached}: 상한 때문에 이번 턴의 ADD 가 하나라도 거부됐다. (T-01 응답의 {@code noteLimitReached})
 */
public record VerificationResult(List<AcceptedChange> accepted, List<RejectedChange> rejected, boolean noteLimitReached) {
}
