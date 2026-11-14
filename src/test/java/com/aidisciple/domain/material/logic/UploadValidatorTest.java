package com.aidisciple.domain.material.logic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class UploadValidatorTest {

	private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);

	private static ErrorCode rejectionOf(String filename, String contentType, byte[] content) {
		Throwable thrown = catchThrowable(() -> UploadValidator.requirePdf(filename, contentType, content));
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return ((BusinessException) thrown).getErrorCode();
	}

	@Test
	void 확장자_MIME_매직바이트가_모두_PDF면_통과한다() {
		assertThat(catchThrowable(() -> UploadValidator.requirePdf("lecture.pdf", "application/pdf", PDF))).isNull();
	}

	@Test
	void 확장자의_대소문자와_MIME의_부가정보는_무시한다() {
		assertThat(catchThrowable(() -> UploadValidator.requirePdf("LECTURE.PDF", "Application/PDF", PDF))).isNull();
		assertThat(catchThrowable(() -> UploadValidator.requirePdf("a.pdf", "application/pdf; charset=binary", PDF))).isNull();
		assertThat(catchThrowable(() -> UploadValidator.requirePdf("a.pdf", "application/x-pdf", PDF))).isNull();
	}

	@Test
	void 확장자만_바꾼_파일은_매직바이트에서_걸린다() {
		byte[] exe = "MZ\u0090\u0000not a pdf".getBytes(StandardCharsets.ISO_8859_1);

		assertThat(rejectionOf("fake.pdf", "application/pdf", exe)).isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
	}

	@Test
	void PDF가_아닌_확장자나_MIME은_거부한다() {
		assertThat(rejectionOf("notes.txt", "application/pdf", PDF)).isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
		assertThat(rejectionOf("notes.pdf.exe", "application/pdf", PDF)).isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
		assertThat(rejectionOf("noextension", "application/pdf", PDF)).isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
		assertThat(rejectionOf(null, "application/pdf", PDF)).isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
		assertThat(rejectionOf("a.pdf", "text/plain", PDF)).isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
		assertThat(rejectionOf("a.pdf", "application/octet-stream", PDF)).isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
		assertThat(rejectionOf("a.pdf", null, PDF)).isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
	}

	@Test
	void 빈_파일은_거부한다() {
		assertThat(rejectionOf("a.pdf", "application/pdf", new byte[0])).isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
		assertThat(rejectionOf("a.pdf", "application/pdf", null)).isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
	}

	@Test
	void 머리말이_앞_1024바이트_안에_있으면_통과하고_밖이면_거부한다() {
		byte[] withPreamble = new byte[600 + PDF.length];
		java.util.Arrays.fill(withPreamble, (byte) ' ');
		System.arraycopy(PDF, 0, withPreamble, 600, PDF.length);
		byte[] tooLate = new byte[2000 + PDF.length];
		java.util.Arrays.fill(tooLate, (byte) ' ');
		System.arraycopy(PDF, 0, tooLate, 2000, PDF.length);

		assertThat(catchThrowable(() -> UploadValidator.requirePdf("a.pdf", "application/pdf", withPreamble))).isNull();
		assertThat(rejectionOf("a.pdf", "application/pdf", tooLate)).isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
	}

	@Test
	void 파일_크기가_20MB를_넘으면_TOO_LARGE다() {
		byte[] atLimit = new byte[(int) UploadValidator.MAX_FILE_BYTES];
		System.arraycopy(PDF, 0, atLimit, 0, PDF.length);
		byte[] overLimit = new byte[(int) UploadValidator.MAX_FILE_BYTES + 1];
		System.arraycopy(PDF, 0, overLimit, 0, PDF.length);

		assertThat(catchThrowable(() -> UploadValidator.requirePdf("a.pdf", "application/pdf", atLimit))).isNull();
		assertThat(rejectionOf("a.pdf", "application/pdf", overLimit)).isEqualTo(ErrorCode.MATERIAL_TOO_LARGE);
	}

	@Test
	void 저장할_파일_이름에서_경로와_제어문자를_지운다() {
		assertThat(UploadValidator.cleanFilename("lecture.pdf")).isEqualTo("lecture.pdf");
		assertThat(UploadValidator.cleanFilename("C:\\Users\\me\\강의 3주차.pdf")).isEqualTo("강의 3주차.pdf");
		assertThat(UploadValidator.cleanFilename("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
		assertThat(UploadValidator.cleanFilename("a\u0000b\nc.pdf")).isEqualTo("abc.pdf");
	}

	@Test
	void 쓸_수_있는_이름이_없으면_null이고_너무_길면_255자로_자른다() {
		assertThat(UploadValidator.cleanFilename(null)).isNull();
		assertThat(UploadValidator.cleanFilename("   ")).isNull();
		assertThat(UploadValidator.cleanFilename("folder/")).isNull();
		assertThat(UploadValidator.cleanFilename("가".repeat(300) + ".pdf")).hasSize(255);
	}

}
