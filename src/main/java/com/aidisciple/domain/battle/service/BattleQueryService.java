package com.aidisciple.domain.battle.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.battle.dto.BattleResponse;
import com.aidisciple.domain.battle.dto.BattleSummaryResponse;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.course.dto.UnitRef;
import com.aidisciple.domain.course.service.CourseService;
import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.api.Pages;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * B-03 방·진행 상태(폴링), B-06 내 배틀 기록, 그리고 다른 배틀 서비스가 같은 모양으로 답할 때 쓰는 응답 조립.
 * 참가자만 볼 수 있고, 없는 배틀과 남의 배틀은 같은 NOT_FOUND 다. 진행 중에는 문항·답·상대 노트를 담지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattleQueryService {

	private final BattleRepository battleRepository;
	private final BattleParticipantRepository participantRepository;
	private final UserLookupService userLookupService;
	private final CourseService courseService;
	private final UnitService unitService;
	private final Clock clock;

	/** B-03 */
	public BattleResponse get(long userId, long battleId) {
		Battle battle = battleRepository.findById(battleId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		List<BattleParticipant> participants = participantRepository.findByBattleIdOrderByIdAsc(battleId);
		if (participants.stream().noneMatch(participant -> participant.getUserId() == userId)) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return toResponse(battle, participants, userId);
	}

	/** B-02·B-03 의 응답. 만료 작업(J-04)이 돌기 전이라도 입장 시간이 지난 방은 EXPIRED 로 보여 준다. */
	public BattleResponse toResponse(Battle battle, List<BattleParticipant> participants, long viewerId) {
		Map<Long, String> nicknames = userLookupService.nicknames(participants.stream().map(BattleParticipant::getUserId).toList());
		String courseName = courseService.findNames(List.of(battle.getCourseId())).get(battle.getCourseId());
		UnitRef unit = unitService.findRefs(List.of(battle.getUnitId())).get(battle.getUnitId());

		BattleStatus status = battle.isExpiredAt(clock.instant()) ? BattleStatus.EXPIRED : battle.getStatus();
		List<BattleResponse.Participant> views = participants.stream()
			.map(participant -> new BattleResponse.Participant(participant.getUserId(),
				nicknames.getOrDefault(participant.getUserId(), ""), participant.getUserId() == viewerId,
				participant.getStatus(), participant.getSolveStatus()))
			.toList();
		return new BattleResponse(battle.getId(), status, battle.getMode(),
			new BattleResponse.Course(battle.getCourseId(), courseName),
			new BattleResponse.Unit(battle.getUnitId(), unit == null ? null : unit.title()),
			battle.getQuestionCount(), battle.getRoomCode(), battle.getExpiresAt(), views,
			summary(battle, participants, viewerId));
	}

	/**
	 * FINISHED 일 때만. 점수·승패는 채점(WP9)이 참가자에 채워 두고, Elo 변화는 랭킹(WP10)이 생기면 채운다.
	 * 연습 배틀은 상대·승패·Elo 가 없다.
	 */
	private static BattleResponse.Summary summary(Battle battle, List<BattleParticipant> participants, long viewerId) {
		if (battle.getStatus() != BattleStatus.FINISHED) {
			return null;
		}
		BattleParticipant me = participants.stream().filter(p -> p.getUserId() == viewerId).findFirst().orElse(null);
		if (me == null) {
			return null;
		}
		if (battle.isPractice()) {
			return new BattleResponse.Summary(me.getTotalScore(), null, null, null);
		}
		BattleParticipant opponent = participants.stream().filter(p -> p.getUserId() != viewerId).findFirst().orElse(null);
		return new BattleResponse.Summary(me.getTotalScore(), opponent == null ? null : opponent.getTotalScore(),
			me.getResult(), null);
	}

	/** B-06. 내가 참가자로 들어 있는 배틀을 최근 것부터 */
	public PageResponse<BattleSummaryResponse> mine(long userId, BattleMode mode, BattleStatus status, Long courseId,
													Integer page, Integer size) {
		Pageable pageable = Pages.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
		Page<Battle> found = battleRepository.findAll(mineSpec(userId, mode, status, courseId), pageable);
		List<Battle> battles = found.getContent();

		Map<Long, List<BattleParticipant>> participantsByBattle = participantRepository
			.findByBattleIdIn(battles.stream().map(Battle::getId).toList()).stream()
			.collect(Collectors.groupingBy(BattleParticipant::getBattleId));
		List<Long> userIds = participantsByBattle.values().stream().flatMap(List::stream).map(BattleParticipant::getUserId)
			.distinct().toList();
		Map<Long, String> nicknames = userLookupService.nicknames(userIds);
		Map<Long, String> courseNames = courseService.findNames(battles.stream().map(Battle::getCourseId).distinct().toList());
		Map<Long, UnitRef> units = unitService.findRefs(battles.stream().map(Battle::getUnitId).distinct().toList());

		List<BattleSummaryResponse> items = battles.stream()
			.map(battle -> toSummary(battle, participantsByBattle.getOrDefault(battle.getId(), List.of()), userId, nicknames,
				courseNames, units))
			.toList();
		return new PageResponse<>(items, found.getNumber(), found.getSize(), found.getTotalElements(), found.hasNext());
	}

	private BattleSummaryResponse toSummary(Battle battle, List<BattleParticipant> participants, long userId,
											Map<Long, String> nicknames, Map<Long, String> courseNames, Map<Long, UnitRef> units) {
		BattleParticipant me = participants.stream().filter(p -> p.getUserId() == userId).findFirst().orElse(null);
		BattleParticipant opponent = participants.stream().filter(p -> p.getUserId() != userId).findFirst().orElse(null);
		boolean finished = battle.getStatus() == BattleStatus.FINISHED;
		BattleStatus status = battle.isExpiredAt(clock.instant()) ? BattleStatus.EXPIRED : battle.getStatus();
		UnitRef unit = units.get(battle.getUnitId());
		return new BattleSummaryResponse(battle.getId(), battle.getMode(), status,
			new BattleSummaryResponse.Course(battle.getCourseId(), courseNames.get(battle.getCourseId())),
			new BattleSummaryResponse.Unit(battle.getUnitId(), unit == null ? null : unit.title()),
			opponent == null ? null
				: new BattleSummaryResponse.Opponent(opponent.getUserId(), nicknames.getOrDefault(opponent.getUserId(), "")),
			finished && me != null ? me.getTotalScore() : null,
			finished && me != null ? me.getResult() : null,
			battle.getCreatedAt());
	}

	private static Specification<Battle> mineSpec(long userId, BattleMode mode, BattleStatus status, Long courseId) {
		return (root, query, cb) -> {
			List<Predicate> conditions = new ArrayList<>();
			Subquery<Long> mine = query.subquery(Long.class);
			Root<BattleParticipant> participant = mine.from(BattleParticipant.class);
			mine.select(participant.get("id"))
				.where(cb.equal(participant.get("battleId"), root.get("id")), cb.equal(participant.get("userId"), userId));
			conditions.add(cb.exists(mine));
			if (mode != null) {
				conditions.add(cb.equal(root.get("mode"), mode));
			}
			if (status != null) {
				conditions.add(cb.equal(root.get("status"), status));
			}
			if (courseId != null) {
				conditions.add(cb.equal(root.get("courseId"), courseId));
			}
			return cb.and(conditions.toArray(new Predicate[0]));
		};
	}

}
