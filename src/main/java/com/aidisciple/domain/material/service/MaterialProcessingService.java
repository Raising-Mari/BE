package com.aidisciple.domain.material.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.aidisciple.domain.material.config.MaterialProperties;
import com.aidisciple.domain.material.entity.MaterialFailReason;
import com.aidisciple.domain.material.extract.MaterialProcessingException;
import com.aidisciple.domain.material.extract.PdfTextExtractor;
import com.aidisciple.domain.material.logic.ExtractedPage;
import com.aidisciple.domain.material.logic.SentencePreparer;
import com.aidisciple.domain.material.logic.TextSanitizer;

/**
 * J-01. 글을 뽑아 문장으로 나누고 저장한다. <b>클래스에 트랜잭션을 걸지 않는다</b>: PDF 를 읽는 동안 DB 커넥션을 쥐지 않기 위해서다.
 * DB 를 쓰는 부분은 {@link MaterialStateService} 가 각자 짧은 트랜잭션으로 한다.
 * 파일 내용·추출한 글은 로그에 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialProcessingService {

	private final MaterialStateService stateService;
	private final PdfTextExtractor pdfTextExtractor;
	private final MaterialProperties properties;

	public void process(long materialId, MaterialContent content) {
		if (!stateService.markProcessing(materialId)) {
			return;
		}
		try {
			SentencePreparer.Prepared prepared = SentencePreparer.prepare(extract(content));
			if (prepared.charCount() > properties.maxExtractedChars()) {
				throw new MaterialProcessingException(MaterialFailReason.TOO_LARGE);
			}
			// 글이 거의 없으면 스캔본(그림으로만 된 PDF)으로 본다. 문항을 만들 근거가 없다.
			if (prepared.sentences().isEmpty() || prepared.charCount() < properties.minTextChars()) {
				throw new MaterialProcessingException(MaterialFailReason.NO_TEXT);
			}
			stateService.complete(materialId, prepared);
		} catch (MaterialProcessingException e) {
			stateService.fail(materialId, e.reason());
		} catch (RuntimeException e) {
			// 예상하지 못한 오류. 원인 클래스만 남기고(메시지에 파일 내용이 섞일 수 있다) 자료는 실패로 닫는다.
			log.warn("자료 처리 실패: materialId={}, cause={}", materialId, e.getClass().getSimpleName());
			stateService.fail(materialId, MaterialFailReason.PARSE_ERROR);
		}
	}

	private List<ExtractedPage> extract(MaterialContent content) {
		return switch (content) {
			case MaterialContent.Pdf pdf -> pdfTextExtractor.extract(pdf.bytes());
			case MaterialContent.Text text -> List.of(new ExtractedPage(null, TextSanitizer.clean(text.text())));
		};
	}

}
