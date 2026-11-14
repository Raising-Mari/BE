package com.aidisciple.domain.material.repository;

import java.sql.Types;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.aidisciple.domain.material.logic.PreparedSentence;

/**
 * 문장을 한꺼번에 넣는다. 자료 하나에 수천 줄이 나오는데 JPA 는 IDENTITY 키라 한 줄씩 넣어야 해서 JDBC 배치를 쓴다.
 * 호출하는 서비스의 트랜잭션에 함께 묶인다. 시각은 앱이 정하지 않고 DB 의 UTC 시각을 쓴다(시드 SQL 과 같은 방식).
 */
@Repository
@RequiredArgsConstructor
public class MaterialSentenceWriter {

	private static final int BATCH_SIZE = 500;

	private static final String INSERT = "INSERT INTO material_sentences "
		+ "(material_id, seq, page_no, `text`, normalized_text, text_hash, created_at) "
		+ "VALUES (?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6))";

	private final JdbcTemplate jdbcTemplate;

	public void writeAll(long materialId, List<PreparedSentence> sentences) {
		jdbcTemplate.batchUpdate(INSERT, sentences, BATCH_SIZE, (ps, sentence) -> {
			ps.setLong(1, materialId);
			ps.setInt(2, sentence.seq());
			ps.setObject(3, sentence.pageNo(), Types.INTEGER);
			ps.setString(4, sentence.text());
			ps.setString(5, sentence.normalizedText());
			ps.setString(6, sentence.textHash());
		});
	}

}
