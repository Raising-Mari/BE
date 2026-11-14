package com.aidisciple.domain.review.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aidisciple.domain.review.entity.Report;
import com.aidisciple.domain.review.entity.ReportKind;
import com.aidisciple.domain.review.entity.ReportStatus;

public interface ReportRepository extends JpaRepository<Report, Long> {

	/** REPORT_DUPLICATE: 같은 사람이 같은 답에 같은 종류로 이미 신고했는가 */
	boolean existsByReporterIdAndBattleAnswerIdAndKind(Long reporterId, Long battleAnswerId, ReportKind kind);

	/** X-07 관리자 신고 목록. status 가 null 이면 전부 (정렬은 Pageable 로) */
	@Query("select r from Report r where (:status is null or r.status = :status)")
	Page<Report> search(@Param("status") ReportStatus status, Pageable pageable);

}
