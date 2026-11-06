package com.aidisciple.domain.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.material.entity.Material;
import com.aidisciple.domain.material.entity.MaterialFailReason;
import com.aidisciple.domain.material.entity.MaterialSourceType;
import com.aidisciple.domain.material.entity.MaterialStatus;
import com.aidisciple.domain.material.entity.MaterialUnit;
import com.aidisciple.domain.material.event.MaterialProcessedEvent;
import com.aidisciple.domain.material.logic.ExtractedPage;
import com.aidisciple.domain.material.logic.SentencePreparer;
import com.aidisciple.domain.material.repository.MaterialRepository;
import com.aidisciple.domain.material.repository.MaterialSentenceWriter;
import com.aidisciple.domain.material.repository.MaterialUnitRepository;

class MaterialStateServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
	private static final long ID = 8L;

	private final MaterialRepository materialRepository = mock(MaterialRepository.class);
	private final MaterialUnitRepository materialUnitRepository = mock(MaterialUnitRepository.class);
	private final MaterialSentenceWriter sentenceWriter = mock(MaterialSentenceWriter.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final MaterialStateService service = new MaterialStateService(materialRepository, materialUnitRepository,
		sentenceWriter, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

	private static Material material(MaterialStatus status) {
		Material material = Material.accept(5L, 42L, "자료", MaterialSourceType.TEXT, null, null, NOW);
		ReflectionTestUtils.setField(material, "id", ID);
		ReflectionTestUtils.setField(material, "status", status);
		return material;
	}

	private static SentencePreparer.Prepared prepared() {
		return SentencePreparer.prepare(List.of(new ExtractedPage(null, "첫 번째 문장이다.\n두 번째 문장이다.")));
	}

	// --- 접수 ---

	@Test
	void 접수하면_PENDING으로_저장하고_동의_시각을_남긴다() {
		when(materialRepository.saveAndFlush(any(Material.class))).thenAnswer(invocation -> {
			Material saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", ID);
			return saved;
		});

		long id = service.register(5L, 42L, "자료", MaterialSourceType.PDF, "a.pdf", 1234L);

		ArgumentCaptor<Material> saved = ArgumentCaptor.forClass(Material.class);
		verify(materialRepository).saveAndFlush(saved.capture());
		assertThat(id).isEqualTo(ID);
		assertThat(saved.getValue().getStatus()).isEqualTo(MaterialStatus.PENDING);
		assertThat(saved.getValue().getConsentAgreedAt()).isEqualTo(NOW);
		assertThat(saved.getValue().getOriginalFilename()).isEqualTo("a.pdf");
		assertThat(saved.getValue().getFileSizeBytes()).isEqualTo(1234L);
	}

	@Test
	void 접수를_취소하면_보이지_않는_상태가_된다() {
		Material pending = material(MaterialStatus.PENDING);
		when(materialRepository.findById(ID)).thenReturn(Optional.of(pending));

		service.abandon(ID);

		assertThat(pending.getStatus()).isEqualTo(MaterialStatus.DELETED);
		assertThat(pending.isVisibleToOwner()).isFalse();
	}

	// --- 처리 시작 ---

	@Test
	void PENDING만_처리를_시작할_수_있고_두_번째_시작은_거부한다() {
		Material pending = material(MaterialStatus.PENDING);
		when(materialRepository.findById(ID)).thenReturn(Optional.of(pending));

		assertThat(service.markProcessing(ID)).isTrue();
		assertThat(pending.getStatus()).isEqualTo(MaterialStatus.PROCESSING);
		assertThat(service.markProcessing(ID)).isFalse();
	}

	@Test
	void 없는_자료나_이미_끝난_자료는_처리를_시작하지_않는다() {
		when(materialRepository.findById(ID)).thenReturn(Optional.empty());
		assertThat(service.markProcessing(ID)).isFalse();

		when(materialRepository.findById(9L)).thenReturn(Optional.of(material(MaterialStatus.DONE)));
		assertThat(service.markProcessing(9L)).isFalse();
	}

	// --- 완료 ---

	@Test
	void 완료하면_문장을_저장하고_DONE으로_바꾸고_연결된_주차와_함께_알린다() {
		Material processing = material(MaterialStatus.PROCESSING);
		when(materialRepository.findById(ID)).thenReturn(Optional.of(processing));
		when(materialUnitRepository.findByIdMaterialId(ID)).thenReturn(List.of(MaterialUnit.link(ID, 9L), MaterialUnit.link(ID, 3L)));
		SentencePreparer.Prepared prepared = prepared();

		service.complete(ID, prepared);

		verify(sentenceWriter).writeAll(ID, prepared.sentences());
		assertThat(processing.getStatus()).isEqualTo(MaterialStatus.DONE);
		assertThat(processing.getSentenceCount()).isEqualTo(2);
		assertThat(processing.getCharCount()).isEqualTo(prepared.charCount());
		assertThat(processing.getProcessedAt()).isEqualTo(NOW);
		assertThat(processing.getFailReason()).isNull();

		ArgumentCaptor<MaterialProcessedEvent> event = ArgumentCaptor.forClass(MaterialProcessedEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue()).isEqualTo(new MaterialProcessedEvent(ID, 5L, List.of(3L, 9L)));
	}

	@Test
	void 처리_중이_아닌_자료에는_문장을_쓰지_않는다() {
		when(materialRepository.findById(ID)).thenReturn(Optional.of(material(MaterialStatus.DELETED)));
		when(materialRepository.findById(9L)).thenReturn(Optional.empty());

		service.complete(ID, prepared());
		service.complete(9L, prepared());

		verifyNoInteractions(sentenceWriter, eventPublisher);
	}

	// --- 실패 ---

	@Test
	void 처리_중이거나_대기_중인_자료만_실패로_닫는다() {
		Material processing = material(MaterialStatus.PROCESSING);
		Material pending = material(MaterialStatus.PENDING);
		Material done = material(MaterialStatus.DONE);
		when(materialRepository.findById(1L)).thenReturn(Optional.of(processing));
		when(materialRepository.findById(2L)).thenReturn(Optional.of(pending));
		when(materialRepository.findById(3L)).thenReturn(Optional.of(done));

		service.fail(1L, MaterialFailReason.NO_TEXT);
		service.fail(2L, MaterialFailReason.PARSE_ERROR);
		service.fail(3L, MaterialFailReason.PARSE_ERROR);

		assertThat(processing.getStatus()).isEqualTo(MaterialStatus.FAILED);
		assertThat(processing.getFailReason()).isEqualTo(MaterialFailReason.NO_TEXT);
		assertThat(processing.getProcessedAt()).isEqualTo(NOW);
		assertThat(pending.getFailReason()).isEqualTo(MaterialFailReason.PARSE_ERROR);
		assertThat(done.getStatus()).isEqualTo(MaterialStatus.DONE);   // 끝난 자료는 되돌리지 않는다
		assertThat(done.getFailReason()).isNull();
	}

	@Test
	void 서버_재시작_때_끝나지_못한_자료를_모두_SERVER_RESTART로_닫는다() {
		Material pending = material(MaterialStatus.PENDING);
		Material processing = material(MaterialStatus.PROCESSING);
		when(materialRepository.findByStatusIn(anyCollection())).thenReturn(List.of(pending, processing));

		int closed = service.failInterrupted();

		assertThat(closed).isEqualTo(2);
		assertThat(List.of(pending, processing)).allSatisfy(material -> {
			assertThat(material.getStatus()).isEqualTo(MaterialStatus.FAILED);
			assertThat(material.getFailReason()).isEqualTo(MaterialFailReason.SERVER_RESTART);
		});
	}

	@Test
	void 끝나지_못한_자료가_없으면_0이다() {
		when(materialRepository.findByStatusIn(anyCollection())).thenReturn(List.of());

		assertThat(service.failInterrupted()).isZero();
		verify(materialRepository, never()).saveAndFlush(any());
	}


}
