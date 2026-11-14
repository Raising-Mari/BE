package com.aidisciple.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.parser.PartTree;

import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.ranking.entity.EloHistory;
import com.aidisciple.domain.ranking.entity.EloRating;
import com.aidisciple.domain.ranking.repository.EloHistoryRepository;
import com.aidisciple.domain.ranking.repository.EloRatingRepository;

/**
 * 리포지토리의 메서드 이름으로 만드는 쿼리(findBy…, countBy…)가 엔티티의 속성과 맞는지 DB 없이 확인한다.
 * 이름이 틀리면 서버가 시작될 때 리포지토리를 만들다 실패해 서버가 통째로 뜨지 않는데, 그 확인은 DB 가 있어야 하는 통합 테스트에서만
 * 됐다. Spring Data 의 파서({@link PartTree})를 그대로 써서 같은 검사를 먼저 한다. {@code @Query} 로 직접 쓴 쿼리는 대상이 아니다.
 */
class DerivedQueryNamesTest {

	private static final List<String> PREFIXES = List.of("find", "count", "exists", "delete", "read", "get", "query", "stream");

	private static void assertAllDerivedNamesParse(Class<?> repository, Class<?> entity) {
		List<Method> derived = Arrays.stream(repository.getDeclaredMethods())
			.filter(method -> !method.isAnnotationPresent(Query.class) && !method.isDefault() && !method.isSynthetic())
			.filter(method -> PREFIXES.stream().anyMatch(method.getName()::startsWith))
			.toList();
		assertThat(derived).as("%s 에서 검사할 메서드", repository.getSimpleName()).isNotEmpty();
		for (Method method : derived) {
			// 속성 이름이 엔티티에 없으면 PropertyReferenceException 이 난다
			new PartTree(method.getName(), entity);
		}
	}

	@Test
	void 랭킹_리포지토리의_메서드_이름이_엔티티_속성과_맞는다() {
		assertAllDerivedNamesParse(EloRatingRepository.class, EloRating.class);
		assertAllDerivedNamesParse(EloHistoryRepository.class, EloHistory.class);
	}

	@Test
	void 배틀_리포지토리의_메서드_이름이_엔티티_속성과_맞는다() {
		assertAllDerivedNamesParse(BattleRepository.class, Battle.class);
		assertAllDerivedNamesParse(BattleParticipantRepository.class, BattleParticipant.class);
	}

	@Test
	void 사용자_리포지토리의_메서드_이름이_엔티티_속성과_맞는다() {
		assertAllDerivedNamesParse(UserRepository.class, User.class);
	}

	@Test
	void 틀린_이름은_실제로_걸러진다() {
		// 검사가 아무것도 못 잡는 헛검사가 아님을 보인다
		assertThatThrownBy(() -> new PartTree("findByCourseIdAndNoSuchField", EloRating.class))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("noSuchField");
	}

}
