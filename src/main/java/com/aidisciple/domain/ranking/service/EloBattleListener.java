package com.aidisciple.domain.ranking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.event.BattleFinishedEvent;

/**
 * 배틀 도메인의 {@link BattleFinishedEvent} 를 받아 Elo 를 반영한다. 배틀 도메인은 랭킹을 모르고, 이 클래스가 둘을 잇는 곳이다.
 * <p>
 * 일부러 {@code @TransactionalEventListener} 가 아니라 평범한 {@code @EventListener} 다: 채점 정산과 <b>같은 트랜잭션</b>에서 돌아야
 * "FINISHED 이면 Elo 도 반영돼 있다"가 지켜진다(결과 화면이 Elo 변화 없이 뜨는 순간이 없다). 반영이 실패하면 정산이 통째로 되돌려져
 * 배틀은 RUNNING 으로 남고, 서버가 다시 시작될 때 이어서 채점된다. 연습 배틀은 건너뛴다.
 */
@Component
@RequiredArgsConstructor
public class EloBattleListener {

	private final EloService eloService;

	@EventListener
	public void onBattleFinished(BattleFinishedEvent event) {
		if (event.mode() == BattleMode.RANKED) {
			eloService.apply(event.battleId());
		}
	}

}
