package com.aidisciple.domain.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.aidisciple.domain.material.config.MaterialProperties;
import com.aidisciple.domain.material.entity.MaterialFailReason;
import com.aidisciple.domain.material.extract.MaterialProcessingException;
import com.aidisciple.domain.material.extract.PdfTextExtractor;
import com.aidisciple.domain.material.logic.ExtractedPage;
import com.aidisciple.domain.material.logic.SentencePreparer;

/** 처리 흐름의 분기. 실제 PDF 는 PdfTextExtractorTest 가, DB 는 통합 테스트가 맡고 여기서는 둘 다 가짜로 둔다. */
class MaterialProcessingServiceTest {

	private static final long ID = 7L;
	private static final byte[] ANY_PDF = {1, 2, 3};
	private static final String LONG_ENOUGH = "스택은 나중에 넣은 것이 먼저 나오는 구조이다. 큐는 먼저 넣은 것이 먼저 나오는 구조이다.";

	private final MaterialStateService stateService = mock(MaterialStateService.class);
	private final PdfTextExtractor pdfTextExtractor = mock(PdfTextExtractor.class);
	private MaterialProcessingService service = new MaterialProcessingService(stateService, pdfTextExtractor, MaterialProperties.defaults());

	@BeforeEach
	void 처리를_시작할_수_있는_자료() {
		when(stateService.markProcessing(ID)).thenReturn(true);
	}

	@Test
	void 붙여넣은_글은_문장으로_나눠_저장하고_완료한다() {
		service.process(ID, new MaterialContent.Text(LONG_ENOUGH));

		ArgumentCaptor<SentencePreparer.Prepared> prepared = ArgumentCaptor.forClass(SentencePreparer.Prepared.class);
		verify(stateService).complete(eq(ID), prepared.capture());
		assertThat(prepared.getValue().sentences()).hasSize(2);
		assertThat(prepared.getValue().sentences().get(0).pageNo()).isNull();
		verify(stateService, never()).fail(eq(ID), any());
		verifyNoInteractions(pdfTextExtractor);
	}

	@Test
	void PDF는_추출기가_준_쪽별_글로_저장한다() {
		when(pdfTextExtractor.extract(ANY_PDF)).thenReturn(List.of(
			new ExtractedPage(1, "첫 쪽의 충분히 긴 문장이다. 두 번째 문장도 있다."), new ExtractedPage(2, "둘째 쪽의 문장이다.")));

		service.process(ID, new MaterialContent.Pdf(ANY_PDF));

		ArgumentCaptor<SentencePreparer.Prepared> prepared = ArgumentCaptor.forClass(SentencePreparer.Prepared.class);
		verify(stateService).complete(eq(ID), prepared.capture());
		assertThat(prepared.getValue().sentences()).extracting(s -> s.pageNo()).containsExactly(1, 1, 2);
	}

	@Test
	void 글이_거의_없으면_스캔본으로_보고_NO_TEXT로_닫는다() {
		when(pdfTextExtractor.extract(ANY_PDF)).thenReturn(List.of(new ExtractedPage(1, ""), new ExtractedPage(2, "  ")));

		service.process(ID, new MaterialContent.Pdf(ANY_PDF));

		verify(stateService).fail(ID, MaterialFailReason.NO_TEXT);
		verify(stateService, never()).complete(eq(ID), any());
	}

	@Test
	void 글자_수가_최소_기준보다_적으면_NO_TEXT다() {
		service.process(ID, new MaterialContent.Text("짧은 글이다."));

		verify(stateService).fail(ID, MaterialFailReason.NO_TEXT);
	}

	@Test
	void 붙여넣은_글이_추출_상한을_넘으면_TOO_LARGE다() {
		service = new MaterialProcessingService(stateService, pdfTextExtractor, new MaterialProperties(100_000, 50, 500, 30));

		service.process(ID, new MaterialContent.Text(LONG_ENOUGH + " " + LONG_ENOUGH));

		verify(stateService).fail(ID, MaterialFailReason.TOO_LARGE);
		verify(stateService, never()).complete(eq(ID), any());
	}

	@Test
	void 추출기가_알려_준_실패_이유가_그대로_기록된다() {
		when(pdfTextExtractor.extract(ANY_PDF)).thenThrow(new MaterialProcessingException(MaterialFailReason.TOO_LARGE));

		service.process(ID, new MaterialContent.Pdf(ANY_PDF));

		verify(stateService).fail(ID, MaterialFailReason.TOO_LARGE);
	}

	@Test
	void 예상하지_못한_오류도_자료를_PARSE_ERROR로_닫아_PROCESSING에_갇히지_않는다() {
		when(pdfTextExtractor.extract(ANY_PDF)).thenThrow(new IllegalStateException("뜻밖의 오류"));

		service.process(ID, new MaterialContent.Pdf(ANY_PDF));

		verify(stateService).fail(ID, MaterialFailReason.PARSE_ERROR);
	}

	@Test
	void 저장_단계에서_실패해도_자료를_닫는다() {
		org.mockito.Mockito.doThrow(new IllegalStateException("DB 오류")).when(stateService).complete(eq(ID), any());

		service.process(ID, new MaterialContent.Text(LONG_ENOUGH));

		verify(stateService).fail(ID, MaterialFailReason.PARSE_ERROR);
	}

	@Test
	void 이미_시작했거나_없어진_자료는_건드리지_않는다() {
		when(stateService.markProcessing(ID)).thenReturn(false);

		service.process(ID, new MaterialContent.Pdf(ANY_PDF));

		verify(stateService, never()).complete(eq(ID), any());
		verify(stateService, never()).fail(eq(ID), any());
		verifyNoInteractions(pdfTextExtractor);
	}

	@Test
	void 붙여넣은_글의_제어문자는_저장_전에_지운다() {
		service.process(ID, new MaterialContent.Text(LONG_ENOUGH.replace("스택은", "스택\u0000은")));

		ArgumentCaptor<SentencePreparer.Prepared> prepared = ArgumentCaptor.forClass(SentencePreparer.Prepared.class);
		verify(stateService).complete(eq(ID), prepared.capture());
		assertThat(prepared.getValue().sentences().get(0).text()).startsWith("스택은").doesNotContain("\u0000");
	}

}
