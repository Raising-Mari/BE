package com.aidisciple.domain.material.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aidisciple.domain.material.PdfFixtures;
import com.aidisciple.domain.material.config.MaterialProperties;
import com.aidisciple.domain.material.entity.MaterialFailReason;
import com.aidisciple.domain.material.logic.ExtractedPage;

class PdfTextExtractorTest {

	private final PdfTextExtractor extractor = new PdfTextExtractor(MaterialProperties.defaults());

	private static MaterialFailReason failureOf(PdfTextExtractor extractor, byte[] pdf) {
		Throwable thrown = catchThrowable(() -> extractor.extract(pdf));
		assertThat(thrown).isInstanceOf(MaterialProcessingException.class);
		return ((MaterialProcessingException) thrown).reason();
	}

	@Test
	void 쪽마다_글을_뽑고_쪽수를_붙인다() {
		byte[] pdf = PdfFixtures.pdf("Stack is last in first out.\nQueue is first in first out.", "Second page sentence.");

		List<ExtractedPage> pages = extractor.extract(pdf);

		assertThat(pages).extracting(ExtractedPage::pageNo).containsExactly(1, 2);
		assertThat(pages.get(0).text()).contains("Stack is last in first out.").contains("Queue is first in first out.");
		assertThat(pages.get(1).text()).contains("Second page sentence.");
	}

	@Test
	void 줄은_위에서_아래로_읽는다() {
		List<ExtractedPage> pages = extractor.extract(PdfFixtures.pdf("first line\nsecond line\nthird line"));

		String text = pages.get(0).text();
		assertThat(text.indexOf("first line")).isLessThan(text.indexOf("second line"));
		assertThat(text.indexOf("second line")).isLessThan(text.indexOf("third line"));
	}

	@Test
	void 글자가_없는_쪽은_빈_글로_남긴다() {
		List<ExtractedPage> pages = extractor.extract(PdfFixtures.pdf("", "Only the second page has text."));

		assertThat(pages).hasSize(2);
		assertThat(pages.get(0).text().isBlank()).isTrue();
		assertThat(pages.get(1).text()).contains("Only the second page has text.");
	}

	@Test
	void 모든_쪽에_글자가_없어도_추출_자체는_성공한다() {
		List<ExtractedPage> pages = extractor.extract(PdfFixtures.pdf("", ""));

		assertThat(pages).hasSize(2);
		assertThat(pages).allSatisfy(page -> assertThat(page.text().isBlank()).isTrue());
	}

	@Test
	void 쪽수가_상한을_넘으면_TOO_LARGE다() {
		PdfTextExtractor small = new PdfTextExtractor(new MaterialProperties(100_000, 500_000, 2, 30));

		assertThat(failureOf(small, PdfFixtures.pdf("one", "two", "three"))).isEqualTo(MaterialFailReason.TOO_LARGE);
		assertThat(small.extract(PdfFixtures.pdf("one", "two"))).hasSize(2);
	}

	@Test
	void 뽑은_글자_수가_상한을_넘으면_TOO_LARGE다() {
		PdfTextExtractor small = new PdfTextExtractor(new MaterialProperties(100_000, 20, 500, 30));

		assertThat(failureOf(small, PdfFixtures.pdf("This sentence is definitely longer than twenty characters."))).isEqualTo(MaterialFailReason.TOO_LARGE);
	}

	@Test
	void 열_때_암호가_필요한_PDF는_PARSE_ERROR다() {
		assertThat(failureOf(extractor, PdfFixtures.passwordProtectedPdf("secret text"))).isEqualTo(MaterialFailReason.PARSE_ERROR);
	}

	@Test
	void 깨진_파일이나_PDF가_아닌_내용은_PARSE_ERROR다() {
		assertThat(failureOf(extractor, "%PDF-1.4 this is not really a pdf".getBytes(StandardCharsets.US_ASCII)))
			.isEqualTo(MaterialFailReason.PARSE_ERROR);
		assertThat(failureOf(extractor, "plain text".getBytes(StandardCharsets.UTF_8))).isEqualTo(MaterialFailReason.PARSE_ERROR);
		assertThat(failureOf(extractor, new byte[0])).isEqualTo(MaterialFailReason.PARSE_ERROR);
	}

	@Test
	void 예외_메시지에는_파일_내용이_담기지_않는다() {
		Throwable thrown = catchThrowable(() -> extractor.extract(PdfFixtures.passwordProtectedPdf("very secret lecture text")));

		assertThat(thrown.getMessage()).isEqualTo("PARSE_ERROR");
		assertThat(thrown.getMessage()).doesNotContain("lecture");
	}

}
