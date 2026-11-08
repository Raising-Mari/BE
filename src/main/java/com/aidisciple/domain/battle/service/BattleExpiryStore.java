package com.aidisciple.domain.battle.service;

import java.time.Clock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.repository.BattleRepository;

/** J-04 의 DB 단계. 방 하나를 잠그고 만료시킨다. 방마다 트랜잭션이 따로라 한 방의 실패가 다른 방을 막지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattleExpiryStore {

	private final BattleRepository battleRepository;
	private final Clock clock;

	/**
	 * 잠근 뒤에 다시 확인한다 — 찾은 뒤 그 사이에 상대가 들어와 시작했거나 취소됐을 수 있다.
	 *
	 * @return 이번에 만료시켰으면 true
	 */
	@Transactional
	public boolean expire(long battleId) {
		Battle battle = battleRepository.findLockedById(battleId).orElse(null);
		if (battle == null || !battle.isExpiredAt(clock.instant())) {
			return false;
		}
		battle.expire();
		return true;
	}

}
