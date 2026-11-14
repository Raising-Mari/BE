package com.aidisciple.global;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import com.aidisciple.domain.battle.config.BattleProperties;
import com.aidisciple.domain.grading.config.GradingProperties;
import com.aidisciple.domain.grading.config.ScoringProperties;

/**
 * 실제 application.yml 의 설정 블록이 속성 클래스에 제대로 묶이는지 확인한다. 서버를 띄우지 않아(DB 불필요) 키 이름 오타나 타입 오류를
 * 빨리 잡는다. 틀린 키는 조용히 무시되어 코드의 기본값으로 돌아가므로, 값이 yml 에 쓴 대로 들어왔는지까지 본다.
 */
class ApplicationYmlBindingTest {

	private static Binder binder() throws IOException {
		var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
		return new Binder(ConfigurationPropertySources.from(sources.get(0)));
	}

	@Test
	void 점수_설정이_yml대로_묶인다() throws IOException {
		ScoringProperties scoring = binder().bind("app.scoring", ScoringProperties.class).get();

		assertThat(scoring.correct()).isEqualByComparingTo(new BigDecimal("1"));
		assertThat(scoring.partial()).isEqualByComparingTo(new BigDecimal("0.5"));
		assertThat(scoring.wrongAnswerList()).isEqualByComparingTo(new BigDecimal("0.5"));
		assertThat(scoring.wrongKeyElements()).isEqualByComparingTo(new BigDecimal("0.3"));
		assertThat(scoring.copyLowRatio()).isEqualTo(0.5);
		assertThat(scoring.copyHighRatio()).isEqualTo(0.9);
		assertThat(scoring.copyMinFactor()).isEqualByComparingTo(new BigDecimal("0.5"));
		assertThat(scoring.copyNgram()).isEqualTo(8);
	}

	@Test
	void 채점_설정이_yml대로_묶인다() throws IOException {
		GradingProperties grading = binder().bind("app.grading", GradingProperties.class).get();

		assertThat(grading.llmBatchSize()).isEqualTo(12);
		assertThat(grading.llmMaxAttempts()).isEqualTo(2);
		assertThat(grading.llmRetryBackoff()).isEqualTo(Duration.ofSeconds(1));
		assertThat(grading.causeOverlapThreshold()).isEqualTo(0.25);
		assertThat(grading.injectionPatterns()).contains("정답으로 처리", "만점", "ignore", "system prompt");
	}

	@Test
	void 배틀_설정이_yml대로_묶인다() throws IOException {
		BattleProperties battle = binder().bind("app.battle", BattleProperties.class).get();

		assertThat(battle.roomTtl()).isEqualTo(Duration.ofMinutes(30));
		assertThat(battle.solveMaxAttempts()).isEqualTo(3);
		assertThat(battle.solveRetryBackoff()).isEqualTo(Duration.ofSeconds(2));
	}

	@Test
	void 코드_기본값과_yml_값이_같다() throws IOException {
		assertThat(binder().bind("app.scoring", ScoringProperties.class).get())
			.usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
			.isEqualTo(ScoringProperties.defaults());
		assertThat(binder().bind("app.battle", BattleProperties.class).get()).isEqualTo(BattleProperties.defaults());
	}

}
