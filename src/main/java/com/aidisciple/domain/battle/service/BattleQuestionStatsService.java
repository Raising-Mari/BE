package com.aidisciple.domain.battle.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.battle.dto.QuestionUsage;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;

/** 관리자 문항 관리(X-03·X-04)가 문항의 배틀 사용 횟수·정답률을 읽는 창구. 개인 정보는 담지 않고 숫자만 준다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattleQuestionStatsService {

	private final BattleQuestionRepository battleQuestionRepository;

	/** 요청한 문항마다 값이 있다(배틀에 안 나온 문항은 0) */
	public Map<Long, QuestionUsage> usageOf(Collection<Long> questionIds) {
		if (questionIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Long> used = new HashMap<>();
		for (Object[] row : battleQuestionRepository.countUsage(questionIds)) {
			used.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
		}
		Map<Long, long[]> answers = new HashMap<>();
		for (Object[] row : battleQuestionRepository.countAnswers(questionIds, Verdict.CORRECT, Verdict.PENDING)) {
			long correct = row[2] == null ? 0 : ((Number) row[2]).longValue();
			answers.put(((Number) row[0]).longValue(), new long[] {((Number) row[1]).longValue(), correct});
		}
		Map<Long, QuestionUsage> result = new HashMap<>();
		for (Long questionId : questionIds) {
			long[] answered = answers.getOrDefault(questionId, new long[] {0, 0});
			result.put(questionId, new QuestionUsage(questionId, used.getOrDefault(questionId, 0L), answered[0], answered[1]));
		}
		return result;
	}

}
