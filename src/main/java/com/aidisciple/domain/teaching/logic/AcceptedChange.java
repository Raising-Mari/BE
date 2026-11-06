package com.aidisciple.domain.teaching.logic;

import java.util.List;

/**
 * 검증을 통과해 저장해도 되는 변경. 새 노트의 id 는 저장할 때 정해지므로 여기에는 없다.
 * 서비스가 이 순서대로 저장한다 (api-spec 11-1 의 6단계).
 */
public sealed interface AcceptedChange {

	/**
	 * 새 노트.
	 * {@code mergedNoteIds}: 유사도가 자동 병합 기준 이상이라 {@code MERGED} 로 바꿀 옛 노트 (새 노트로 흡수된다).
	 * {@code suggestions}: 자동 병합까지는 아니지만 비슷해서 학생에게 "합칠까요?"를 물을 옛 노트 (유사도 높은 순).
	 */
	record Add(String content, String evidence, List<Long> mergedNoteIds, List<MergeCandidate> suggestions)
		implements AcceptedChange {
	}

	/** 옛 노트({@code targetId})는 {@code SUPERSEDED} 가 되고 새 노트가 생긴다. */
	record Update(long targetId, String content) implements AcceptedChange {
	}

	/** 옛 노트({@code targetId})가 {@code REMOVED} 가 된다. */
	record Remove(long targetId) implements AcceptedChange {
	}

	/** 합치기를 제안할 옛 노트와 새 노트와의 유사도 */
	record MergeCandidate(long noteId, double similarity) {
	}

}
