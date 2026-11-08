package com.aidisciple.domain.material.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.material.entity.Material;
import com.aidisciple.domain.material.entity.MaterialStatus;

public interface MaterialRepository extends JpaRepository<Material, Long> {

	/** 내가 올린 자료. 삭제·접수 취소된 것은 빼고 최근 것이 먼저 오도록 id 내림차순 */
	List<Material> findByCourseIdAndUploaderIdAndStatusNotOrderByIdDesc(Long courseId, Long uploaderId, MaterialStatus excluded);

	/** 서버가 재시작되면 메모리에 있던 파일 내용이 사라지므로, 끝나지 못한 자료를 찾아 실패로 돌린다. */
	List<Material> findByStatusIn(Collection<MaterialStatus> statuses);

	List<Material> findByIdInAndStatusOrderByIdAsc(Collection<Long> ids, MaterialStatus status);
}
