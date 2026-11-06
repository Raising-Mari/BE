package com.aidisciple.domain.material.logic;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * M-01 업로드 파일 검사. 확장자·MIME·매직 바이트를 모두 확인한다. 하나만 보면 이름만 바꾼 파일이 통과한다. (api-spec 3절)
 * DB·Spring 을 모르는 순수 클래스다.
 */
public final class UploadValidator {

	/** {@code spring.servlet.multipart.max-file-size} 와 같은 값. 어긋나면 서버가 받기도 전에 거부하거나 여기서 거부한다. */
	public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

	private static final int MAX_FILENAME_LENGTH = 255;
	private static final Set<String> PDF_MIME_TYPES = Set.of("application/pdf", "application/x-pdf");
	private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	/** PDF 규격은 머리말이 파일 앞 1024바이트 안에 있으면 된다고 허용한다. */
	private static final int MAGIC_SEARCH_WINDOW = 1024;

	private UploadValidator() {
	}

	public static void requirePdf(String filename, String contentType, byte[] content) {
		if (content == null || content.length == 0) {
			throw new BusinessException(ErrorCode.MATERIAL_INVALID_FILE);
		}
		if (content.length > MAX_FILE_BYTES) {
			throw new BusinessException(ErrorCode.MATERIAL_TOO_LARGE);
		}
		if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
			throw new BusinessException(ErrorCode.MATERIAL_INVALID_FILE);
		}
		if (!PDF_MIME_TYPES.contains(mimeTypeOf(contentType))) {
			throw new BusinessException(ErrorCode.MATERIAL_INVALID_FILE);
		}
		if (!startsWithPdfMagic(content)) {
			throw new BusinessException(ErrorCode.MATERIAL_INVALID_FILE);
		}
	}

	/**
	 * 저장할 파일 이름. 경로 부분과 제어 문자를 지우고 255자로 자른다. 쓸 수 있는 이름이 없으면 null.
	 * (파일 이름은 목록 표시에만 쓰고 파일 시스템 경로로는 절대 쓰지 않는다.)
	 */
	public static String cleanFilename(String filename) {
		if (filename == null) {
			return null;
		}
		int lastSeparator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
		String name = filename.substring(lastSeparator + 1).replaceAll("\\p{Cntrl}", "").strip();
		if (name.isEmpty()) {
			return null;
		}
		return name.length() > MAX_FILENAME_LENGTH ? name.substring(0, MAX_FILENAME_LENGTH) : name;
	}

	/** {@code application/pdf; charset=binary} 같은 값에서 부가 정보를 떼고 소문자로 */
	private static String mimeTypeOf(String contentType) {
		if (contentType == null) {
			return "";
		}
		int semicolon = contentType.indexOf(';');
		String base = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
		return base.strip().toLowerCase(Locale.ROOT);
	}

	private static boolean startsWithPdfMagic(byte[] content) {
		int limit = Math.min(content.length, MAGIC_SEARCH_WINDOW) - PDF_MAGIC.length;
		for (int start = 0; start <= limit; start++) {
			boolean match = true;
			for (int i = 0; i < PDF_MAGIC.length; i++) {
				if (content[start + i] != PDF_MAGIC[i]) {
					match = false;
					break;
				}
			}
			if (match) {
				return true;
			}
		}
		return false;
	}

}
