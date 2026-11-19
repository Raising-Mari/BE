package com.aidisciple.domain.question.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.course.service.UnitBattleReadyLookup;
import com.aidisciple.domain.question.config.QuestionProperties;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.repository.QuestionRepository;

/**
 * C-05 {@code battleReady} 의 구현. Q-01 과 같은 기준(내가 아직 안 본, 배틀에 쓸 수 있는 문항 수)을 주차 여럿에 적용한다.
 * {@link QuestionBankService} 는 주차 서비스를 쓰므로 거기에 두면 서로 물고 도는 구조가 되어 따로 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnitBattleReadiness implements UnitBattleReadyLookup {

	private final QuestionRepository questionRepository;
	private final QuestionProperties properties;

	@Override
	public Set<Long> battleReadyUnits(long userId, Collection<Long> unitIds, int questionCount) {
		List<QuestionStatus> usable = properties.usableStatuses();
		return unitIds.stream()
			.filter(unitId -> questionRepository.countUnseen(unitId, usable, userId) >= questionCount)
			.collect(Collectors.toSet());
	}

}
