package com.aidisciple.domain.course.service;

import java.util.Collection;
import java.util.Map;

/** C-05 의 {@code materialCount}. 구현은 material 도메인이 한다({@link UnitNoteCountLookup} 와 같은 이유). */
public interface UnitMaterialCountLookup {

	/** 주차별로 연결된 DONE 자료 수. 자료가 없는 주차는 키가 없다. */
	Map<Long, Long> doneMaterialCounts(Collection<Long> unitIds);

}
