package com.aidisciple.domain.material;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * 테스트용 PDF 를 그 자리에서 만든다. 실제 강의자료 파일을 저장소에 넣지 않기 위해서다.
 * 기본 글꼴(Helvetica)은 한글을 그릴 수 없어서 쪽 글은 영문이다. 한국어 문장 분리는 SentenceSplitter 쪽 테스트가 맡는다.
 */
public final class PdfFixtures {

	private PdfFixtures() {
	}

	/** 쪽마다 글 하나(줄바꿈은 \n). 빈 문자열이면 아무것도 그리지 않은 쪽 — 스캔본처럼 글자가 없는 쪽이다. */
	public static byte[] pdf(String... pageTexts) {
		try (PDDocument document = new PDDocument()) {
			addPages(document, pageTexts);
			return save(document);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** 열 때 암호가 필요한 PDF */
	public static byte[] passwordProtectedPdf(String pageText) {
		try (PDDocument document = new PDDocument()) {
			addPages(document, pageText);
			document.protect(new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission()));
			return save(document);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void addPages(PDDocument document, String... pageTexts) throws IOException {
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		for (String text : pageTexts) {
			PDPage page = new PDPage();
			document.addPage(page);
			if (text.isEmpty()) {
				continue;
			}
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(font, 12);
				content.setLeading(16f);
				content.newLineAtOffset(50, 700);
				for (String line : text.split("\n")) {
					content.showText(line);
					content.newLine();
				}
				content.endText();
			}
		}
	}

	private static byte[] save(PDDocument document) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		document.save(out);
		return out.toByteArray();
	}

}
