package com.aidisciple.domain.material.extract;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.aidisciple.domain.material.config.MaterialProperties;
import com.aidisciple.domain.material.entity.MaterialFailReason;
import com.aidisciple.domain.material.logic.ExtractedPage;
import com.aidisciple.domain.material.logic.TextSanitizer;

/**
 * PDFBox 로 PDF 에서 쪽마다 글을 뽑는다. PDFBox 는 이 클래스 밖으로 나가지 않는다.
 * 글자가 없는 쪽(그림뿐인 쪽)은 빈 글로 남기고, 전체에서 글이 너무 적은지는 호출하는 쪽이 판단한다.
 * 파일 내용·추출한 글은 예외 메시지와 로그에 남기지 않는다.
 */
@Component
public class PdfTextExtractor {

	private final MaterialProperties properties;

	public PdfTextExtractor(MaterialProperties properties) {
		this.properties = properties;
	}

	/**
	 * @throws MaterialProcessingException {@code TOO_LARGE}(쪽수·글자 수 초과), {@code PARSE_ERROR}(암호가 걸렸거나 깨진 파일)
	 */
	public List<ExtractedPage> extract(byte[] pdf) {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			int pageCount = document.getNumberOfPages();
			if (pageCount > properties.maxPages()) {
				throw new MaterialProcessingException(MaterialFailReason.TOO_LARGE);
			}
			PDFTextStripper stripper = new PDFTextStripper();
			// 슬라이드는 글상자가 만들어진 순서와 화면에서 읽는 순서가 다를 수 있어 위치 순서로 읽는다.
			stripper.setSortByPosition(true);

			List<ExtractedPage> pages = new ArrayList<>(pageCount);
			long totalChars = 0;
			for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
				stripper.setStartPage(pageNo);
				stripper.setEndPage(pageNo);
				String text = TextSanitizer.clean(stripper.getText(document));
				totalChars += text.length();
				if (totalChars > properties.maxExtractedChars()) {
					throw new MaterialProcessingException(MaterialFailReason.TOO_LARGE);
				}
				pages.add(new ExtractedPage(pageNo, text));
			}
			return pages;
		} catch (IOException e) {
			// 암호가 걸렸거나(InvalidPasswordException), 형식이 깨졌거나, 추출이 금지된 파일
			throw new MaterialProcessingException(MaterialFailReason.PARSE_ERROR, e);
		}
	}

}
