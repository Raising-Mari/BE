package com.aidisciple.domain.ranking.service;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.battle.service.BattleEloLookup;
import com.aidisciple.domain.ranking.repository.EloHistoryRepository;

/** 배틀 응답(B-03·B-07)에 싣는 Elo 변화. 배틀 도메인이 정한 인터페이스를 랭킹이 구현한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EloChangeLookup implements BattleEloLookup {

	private final EloHistoryRepository historyRepository;

	@Override
	public Optional<EloChange> find(long battleId, long userId) {
		return historyRepository.findByBattleIdAndUserId(battleId, userId)
			.map(history -> new EloChange(history.getRatingBefore(), history.getRatingAfter(), history.getDelta()));
	}

}
