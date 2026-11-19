package com.aidisciple.domain.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.aidisciple.domain.question.config.QuestionProperties;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.repository.QuestionRepository;

class UnitBattleReadinessTest {

	private static final long USER_ID = 42L;

	private final QuestionRepository questionRepository = mock(QuestionRepository.class);

	private UnitBattleReadiness readiness(QuestionProperties properties) {
		return new UnitBattleReadiness(questionRepository, properties);
	}

	@Test
	void 안_본_문항이_기준_이상인_주차만_배틀_가능이다() {
		when(questionRepository.countUnseen(org.mockito.ArgumentMatchers.eq(1L), anyCollection(), org.mockito.ArgumentMatchers.eq(USER_ID))).thenReturn(5L);
		when(questionRepository.countUnseen(org.mockito.ArgumentMatchers.eq(2L), anyCollection(), org.mockito.ArgumentMatchers.eq(USER_ID))).thenReturn(4L);
		when(questionRepository.countUnseen(org.mockito.ArgumentMatchers.eq(3L), anyCollection(), org.mockito.ArgumentMatchers.eq(USER_ID))).thenReturn(12L);

		assertThat(readiness(QuestionProperties.defaults()).battleReadyUnits(USER_ID, List.of(1L, 2L, 3L), 5))
			.isEqualTo(Set.of(1L, 3L));
	}

	@Test
	void 설정이_켜져_있으면_UNJUDGED_문항도_센다() {
		when(questionRepository.countUnseen(anyLong(), anyCollection(), anyLong())).thenReturn(0L);

		readiness(QuestionProperties.defaults()).battleReadyUnits(USER_ID, List.of(1L), 5);

		ArgumentCaptor<java.util.Collection<QuestionStatus>> statuses = ArgumentCaptor.captor();
		verify(questionRepository).countUnseen(anyLong(), statuses.capture(), anyLong());
		assertThat(statuses.getValue()).containsExactlyInAnyOrder(QuestionStatus.VERIFIED, QuestionStatus.UNJUDGED);
	}

	@Test
	void 설정이_꺼져_있으면_VERIFIED_문항만_센다() {
		when(questionRepository.countUnseen(anyLong(), anyCollection(), anyLong())).thenReturn(0L);
		QuestionProperties strict = new QuestionProperties(false, 10, 5, 3, 12000);

		readiness(strict).battleReadyUnits(USER_ID, List.of(1L), 5);

		ArgumentCaptor<java.util.Collection<QuestionStatus>> statuses = ArgumentCaptor.captor();
		verify(questionRepository).countUnseen(anyLong(), statuses.capture(), anyLong());
		assertThat(statuses.getValue()).containsExactly(QuestionStatus.VERIFIED);
	}

}
