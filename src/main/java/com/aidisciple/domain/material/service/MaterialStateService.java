package com.aidisciple.domain.material.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.material.entity.Material;
import com.aidisciple.domain.material.entity.MaterialFailReason;
import com.aidisciple.domain.material.entity.MaterialSourceType;
import com.aidisciple.domain.material.entity.MaterialStatus;
import com.aidisciple.domain.material.entity.MaterialUnit;
import com.aidisciple.domain.material.event.MaterialProcessedEvent;
import com.aidisciple.domain.material.logic.SentencePreparer;
import com.aidisciple.domain.material.repository.MaterialRepository;
import com.aidisciple.domain.material.repository.MaterialSentenceWriter;
import com.aidisciple.domain.material.repository.MaterialUnitRepository;

/**
 * 자료의 상태를 바꾸는 짧은 트랜잭션들. 파일을 읽고 글을 뽑는 긴 작업은 {@link MaterialProcessingService} 가 트랜잭션 밖에서 하고,
 * 그 앞뒤의 DB 쓰기만 여기서 한다. (비동기 작업의 상태는 항상 DB 에 남긴다 — BE AGENTS 14)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialStateService {

	private final MaterialRepository materialRepository;
	private final MaterialUnitRepository materialUnitRepository;
	private final MaterialSentenceWriter sentenceWriter;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Transactional
	public long register(long courseId, long uploaderId, String title, MaterialSourceType sourceType,
		String originalFilename, Long fileSizeBytes) {
		return materialRepository.saveAndFlush(Material.accept(courseId, uploaderId, title, sourceType,
			originalFilename, fileSizeBytes, clock.instant())).getId();
	}

	/** 처리 큐가 가득 차서 접수를 취소한다. 사용자는 이 자료의 id 를 받은 적이 없으므로 보이지 않게만 해 둔다. */
	@Transactional
	public void abandon(long materialId) {
		materialRepository.findById(materialId).ifPresent(Material::abandon);
	}

	/** 처리를 시작해도 되면 true. 이미 시작했거나 없어진 자료면 false 라서 같은 자료를 두 번 처리하지 않는다. */
	@Transactional
	public boolean markProcessing(long materialId) {
		return materialRepository.findById(materialId).map(Material::startProcessing).orElse(false);
	}

	/** 문장을 저장하고 DONE 으로 바꾼 뒤, 이미 연결된 주차와 함께 처리 완료를 알린다. */
	@Transactional
	public void complete(long materialId, SentencePreparer.Prepared prepared) {
		Optional<Material> found = materialRepository.findById(materialId);
		if (found.isEmpty() || found.get().getStatus() != MaterialStatus.PROCESSING) {
			return;
		}
		Material material = found.get();
		sentenceWriter.writeAll(materialId, prepared.sentences());
		material.complete(prepared.charCount(), prepared.sentences().size(), clock.instant());

		List<Long> unitIds = materialUnitRepository.findByIdMaterialId(materialId).stream()
			.map(MaterialUnit::getUnitId)
			.sorted()
			.toList();
		eventPublisher.publishEvent(new MaterialProcessedEvent(materialId, material.getCourseId(), unitIds));
	}

	@Transactional
	public void fail(long materialId, MaterialFailReason reason) {
		materialRepository.findById(materialId)
			.filter(material -> material.getStatus() == MaterialStatus.PROCESSING || material.getStatus() == MaterialStatus.PENDING)
			.ifPresent(material -> material.fail(reason, clock.instant()));
	}

	/**
	 * 서버가 시작될 때 한 번: 끝나지 못한 자료를 실패로 돌린다. 파일 내용은 메모리에만 있어서 재시작하면 사라지므로
	 * 다시 처리할 수 없고, 사용자가 다시 올려야 한다. (api-spec J-06)
	 */
	@Transactional
	public int failInterrupted() {
		List<Material> interrupted = materialRepository.findByStatusIn(List.of(MaterialStatus.PENDING, MaterialStatus.PROCESSING));
		interrupted.forEach(material -> material.fail(MaterialFailReason.SERVER_RESTART, clock.instant()));
		return interrupted.size();
	}

}
