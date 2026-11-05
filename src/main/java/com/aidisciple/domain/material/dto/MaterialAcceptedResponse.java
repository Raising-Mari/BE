package com.aidisciple.domain.material.dto;

import com.aidisciple.domain.material.entity.MaterialStatus;

/** M-01·M-02 접수(202). 처리는 비동기라 FE 는 M-03 으로 상태를 조회한다. */
public record MaterialAcceptedResponse(Long materialId, MaterialStatus status) {
}
