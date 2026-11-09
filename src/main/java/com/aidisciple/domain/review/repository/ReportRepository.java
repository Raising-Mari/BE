package com.aidisciple.domain.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.review.entity.Report;
import com.aidisciple.domain.review.entity.ReportKind;

public interface ReportRepository extends JpaRepository<Report, Long> {

	/** REPORT_DUPLICATE: 같은 사람이 같은 답에 같은 종류로 이미 신고했는가 */
	boolean existsByReporterIdAndBattleAnswerIdAndKind(Long reporterId, Long battleAnswerId, ReportKind kind);

}
