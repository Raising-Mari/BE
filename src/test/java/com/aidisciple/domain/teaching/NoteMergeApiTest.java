package com.aidisciple.domain.teaching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.ai.LlmClient;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.Disciple;
import com.aidisciple.domain.course.entity.Unit;
import com.aidisciple.domain.course.repository.CourseRepository;
import com.aidisciple.domain.course.repository.DiscipleRepository;
import com.aidisciple.domain.course.repository.UnitRepository;
import com.aidisciple.domain.teaching.entity.ChatMessage;
import com.aidisciple.domain.teaching.entity.NoteEntry;
import com.aidisciple.domain.teaching.entity.NoteStatus;
import com.aidisciple.domain.teaching.repository.ChatMessageRepository;
import com.aidisciple.domain.teaching.repository.NoteEntryRepository;
import com.aidisciple.global.security.UserRole;

/** T-05 노트 합치기를 DB 까지 이어서 확인한다. 각 테스트는 트랜잭션으로 감싸 끝나면 되돌린다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoteMergeApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	UserRepository userRepository;

	@Autowired
	SchoolRepository schoolRepository;

	@Autowired
	CourseRepository courseRepository;

	@Autowired
	UnitRepository unitRepository;

	@Autowired
	DiscipleRepository discipleRepository;

	@Autowired
	ChatMessageRepository chatMessageRepository;

	@Autowired
	NoteEntryRepository noteEntryRepository;

	@MockitoBean
	LlmClient llmClient;

	private String suffix;
	private long schoolId;
	private User alice;
	private User bob;
	private long unitId;
	private long otherUnitId;
	private long discipleId;
	private int turnSeq = 0;

	@BeforeEach
	void 앨리스의_제자와_두_주차를_만든다() {
		suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		schoolId = schoolRepository.findByCode("MJU").orElseThrow().getId();
		alice = newUser("a");
		bob = newUser("b");
		String courseName = "자료구조" + suffix;
		long courseId = courseRepository.saveAndFlush(Course.create(schoolId, null, courseName, courseName, "2026-2", alice.getId())).getId();
		unitId = unitRepository.saveAndFlush(Unit.create(courseId, "3주차", "3주차", alice.getId())).getId();
		otherUnitId = unitRepository.saveAndFlush(Unit.create(courseId, "4주차", "4주차", alice.getId())).getId();
		discipleId = discipleRepository.saveAndFlush(Disciple.create(alice.getId(), courseId, "마리", null)).getId();
	}

	@Test
	void 합치면_merge_노트만_MERGED가_되고_keep을_가리키며_내용은_그대로다() throws Exception {
		long older = addNote(unitId, "스택은 나중에 넣은 게 먼저 나온다.");
		long newer = addNote(unitId, "스택은 나중에 넣은 것이 먼저 나오는 구조다.");

		merge(alice, newer, older)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.keepEntryId").value(newer))
			.andExpect(jsonPath("$.mergedEntryId").value(older));

		NoteEntry merged = noteEntryRepository.findById(older).orElseThrow();
		assertThat(merged.getStatus()).isEqualTo(NoteStatus.MERGED);
		assertThat(merged.getMergedIntoEntryId()).isEqualTo(newer);
		assertThat(merged.getContent()).isEqualTo("스택은 나중에 넣은 게 먼저 나온다.");
		assertThat(noteEntryRepository.findById(newer).orElseThrow().getStatus()).isEqualTo(NoteStatus.ACTIVE);
	}

	@Test
	void 같은_노트이거나_이미_합쳐졌거나_다른_주차면_409다() throws Exception {
		long first = addNote(unitId, "큐는 먼저 넣은 것이 먼저 나온다.");
		long second = addNote(unitId, "큐는 먼저 들어간 것이 먼저 나온다.");
		long otherUnit = addNote(otherUnitId, "트리는 계층 구조다.");

		merge(alice, first, first)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TEACHING_NOTE_MERGE_INVALID"));
		merge(alice, first, otherUnit)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TEACHING_NOTE_MERGE_INVALID"));

		merge(alice, second, first).andExpect(status().isOk());
		merge(alice, second, first)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TEACHING_NOTE_MERGE_INVALID"));
	}

	@Test
	void 남의_제자면_404이고_노트는_바뀌지_않는다() throws Exception {
		long first = addNote(unitId, "스택은 LIFO 다.");
		long second = addNote(unitId, "스택은 후입선출이다.");

		merge(bob, second, first).andExpect(status().isNotFound());
		assertThat(noteEntryRepository.findById(first).orElseThrow().getStatus()).isEqualTo(NoteStatus.ACTIVE);
	}

	@Test
	void 본문이_빠지면_400() throws Exception {
		mockMvc.perform(post("/api/disciples/{id}/notes/merge", discipleId).header("Authorization", bearer(alice))
				.contentType(MediaType.APPLICATION_JSON).content("{\"keepEntryId\": 1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	// --- 준비 도우미 ---

	private ResultActions merge(User user, long keepId, long mergeId) throws Exception {
		return mockMvc.perform(post("/api/disciples/{id}/notes/merge", discipleId).header("Authorization", bearer(user))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"keepEntryId\": %d, \"mergeEntryId\": %d}".formatted(keepId, mergeId)));
	}

	private long addNote(long unit, String content) {
		ChatMessage message = chatMessageRepository.saveAndFlush(
			ChatMessage.student(discipleId, unit, ++turnSeq, content, UUID.randomUUID().toString()));
		return noteEntryRepository.saveAndFlush(
			NoteEntry.create(discipleId, unit, content, content, message.getId(), null, 1, "{}")).getId();
	}

	private User newUser(String tag) {
		return userRepository.saveAndFlush(User.create(schoolId, null, "sub-" + tag + suffix,
			tag + suffix + "@mju.ac.kr", tag + suffix, UserRole.STUDENT, Instant.now()));
	}

	private String bearer(User user) {
		return "Bearer " + jwtProvider.issueAccessToken(user.getId(), user.getRole());
	}

}
