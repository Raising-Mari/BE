package com.aidisciple.domain.battle.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.repository.BattleRepository;

/**
 * J-04. 입장 시간이 지난 WAITING·LOBBY 방을 EXPIRED 로 바꾼다(api-spec: 1분마다).
 * 입장·준비·조회는 이 작업이 돌기 전이라도 지난 방을 만료로 보므로, 이 작업은 상태를 정리하고 방 코드를 풀어 주는 역할이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BattleExpiryJob {

	private final BattleRepository battleRepository;
	private final BattleExpiryStore store;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${app.battle.expiry-sweep-interval:PT1M}")
	public void sweep() {
		sweep(clock.instant());
	}

	/** @return 만료시킨 방 수 */
	int sweep(Instant now) {
		List<Long> overdue = battleRepository.findIdsExpiredBefore(BattleStatus.OPEN_ROOM, now);
		int expired = 0;
		for (Long battleId : overdue) {
			try {
				if (store.expire(battleId)) {
					expired++;
				}
			} catch (RuntimeException e) {
				log.warn("방 만료 처리 실패: battleId={}, cause={}", battleId, e.getClass().getSimpleName());
			}
		}
		if (expired > 0) {
			log.info("입장 시간이 지난 방 {}개를 만료 처리했습니다.", expired);
		}
		return expired;
	}

}
