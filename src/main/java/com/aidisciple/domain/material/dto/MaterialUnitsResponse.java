package com.aidisciple.domain.material.dto;

import java.util.List;

/** M-06 */
public record MaterialUnitsResponse(Long materialId, List<Long> unitIds) {
}
