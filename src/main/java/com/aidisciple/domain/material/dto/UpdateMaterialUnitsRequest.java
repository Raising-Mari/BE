package com.aidisciple.domain.material.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** M-06 연결할 주차 전체(교체). 빈 배열이면 연결을 모두 푼다. */
public record UpdateMaterialUnitsRequest(@NotNull List<@NotNull Long> unitIds) {
}
