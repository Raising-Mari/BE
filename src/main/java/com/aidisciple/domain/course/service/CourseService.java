package com.aidisciple.domain.course.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.DepartmentResponse;
import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.DepartmentService;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.course.dto.CourseResponse;
import com.aidisciple.domain.course.dto.CreateCourseRequest;
import com.aidisciple.domain.course.dto.MyCourseResponse;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.CourseStatus;
import com.aidisciple.domain.course.entity.Disciple;
import com.aidisciple.domain.course.logic.NameNormalizer;
import com.aidisciple.domain.course.repository.CourseRepository;
import com.aidisciple.domain.course.repository.DiscipleRepository;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.api.Pages;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/** C-01~C-04. 과목은 학교 안에서만 보인다: 다른 학교의 과목은 있는지조차 알려 주지 않는다(NOT_FOUND). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

	/** 연도-숫자 한 자리. 예: 2026-2. 형식이 제각각이면 같은 과목이 학기만 다르게 적혀 중복 판정이 무너진다. */
	private static final Pattern TERM_FORMAT = Pattern.compile("^\\d{4}-[1-9]$");

	private static final Sort SEARCH_ORDER = Sort.by(Sort.Order.desc("term"), Sort.Order.asc("name"));

	private final CourseRepository courseRepository;
	private final DiscipleRepository discipleRepository;
	private final UserLookupService userLookupService;
	private final DepartmentService departmentService;

	/** C-01. 내 학교의 보관되지 않은 과목을 이름·학과로 찾는다. */
	public PageResponse<CourseResponse> search(long userId, String keyword, Long departmentId, Integer page, Integer size) {
		UserRef user = userLookupService.requireActive(userId);
		Pageable pageable = Pages.of(page, size, SEARCH_ORDER);

		Page<Course> found = courseRepository.findAll(searchSpec(user.schoolId(), keyword, departmentId), pageable);

		List<Long> courseIds = found.getContent().stream().map(Course::getId).toList();
		Set<Long> mine = discipleRepository.findByUserIdAndCourseIdIn(userId, courseIds).stream()
			.map(Disciple::getCourseId)
			.collect(Collectors.toSet());
		Map<Long, DepartmentResponse> departments = departmentsOf(found.getContent());
		return PageResponse.from(found, course -> toResponse(course, departments, mine.contains(course.getId())));
	}

	@Transactional
	public CourseResponse create(long userId, CreateCourseRequest request) {
		UserRef user = userLookupService.requireActive(userId);

		String name = request.name().strip();
		String normalizedName = NameNormalizer.normalize(name);
		if (normalizedName.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "과목 이름을 입력해 주세요.");
		}
		String term = request.term().strip();
		if (!TERM_FORMAT.matcher(term).matches()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "학기는 2026-2 처럼 연도와 숫자 한 자리로 입력해 주세요.");
		}
		DepartmentResponse department = null;
		if (request.departmentId() != null) {
			department = departmentService.findInSchool(request.departmentId(), user.schoolId())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 학과입니다."));
		}

		// 같은 과목이 이미 있으면 새로 만들지 않고 기존 과목 id 를 알려 줘서 FE 가 그 과목으로 이동하게 한다.
		courseRepository.findBySchoolIdAndNormalizedNameAndTerm(user.schoolId(), normalizedName, term)
			.ifPresent(existing -> {
				throw new BusinessException(ErrorCode.COURSE_DUPLICATE, Map.of("existingCourseId", existing.getId()));
			});

		Course course;
		try {
			course = courseRepository.saveAndFlush(Course.create(user.schoolId(),
				department == null ? null : department.id(), name, normalizedName, term, userId));
		} catch (DataIntegrityViolationException e) {
			// 위의 확인을 동시에 통과한 두 요청 중 늦은 쪽. 이 트랜잭션은 더 쓰지 못하므로 기존 id 는 싣지 않는다.
			throw new BusinessException(ErrorCode.COURSE_DUPLICATE);
		}
		return toResponse(course, department == null ? Map.of() : Map.of(department.id(), department), false);
	}

	/** C-03. 제자가 있는 과목 = 내가 수강 중인 과목. 최근에 만든 제자의 과목이 먼저 온다. */
	public List<MyCourseResponse> myCourses(long userId) {
		userLookupService.requireActive(userId);
		List<Disciple> disciples = discipleRepository.findByUserIdOrderByIdDesc(userId);
		Map<Long, Course> courses = courseRepository
			.findAllById(disciples.stream().map(Disciple::getCourseId).toList()).stream()
			.collect(Collectors.toMap(Course::getId, Function.identity()));

		List<MyCourseResponse> result = new ArrayList<>();
		for (Disciple disciple : disciples) {
			Course course = courses.get(disciple.getCourseId());
			if (course != null) {
				result.add(new MyCourseResponse(course.getId(), course.getName(), course.getTerm(),
					new MyCourseResponse.Disciple(disciple.getId(), disciple.getName(), disciple.getCharacterKey())));
			}
		}
		return result;
	}

	/** C-04 */
	public CourseResponse get(long userId, long courseId) {
		UserRef user = userLookupService.requireActive(userId);
		Course course = requireVisible(courseId, user);
		boolean hasMyDisciple = discipleRepository.existsByUserIdAndCourseId(userId, courseId);
		return toResponse(course, departmentsOf(List.of(course)), hasMyDisciple);
	}

	/**
	 * 이 사용자가 볼 수 있는 과목인가(같은 학교). 주차·제자 서비스도 이 검사를 거친다.
	 * 없는 과목과 다른 학교의 과목을 같은 NOT_FOUND 로 답해서 다른 학교의 과목 id 를 알아낼 수 없게 한다.
	 */
	public Course requireVisible(long courseId, UserRef user) {
		return courseRepository.findById(courseId)
			.filter(course -> course.getSchoolId().equals(user.schoolId()))
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	/** 다른 도메인(배틀 등)이 화면에 과목 이름만 보여 줄 때 쓴다. 없는 과목은 결과에 없다. */
	public Map<Long, String> findNames(Collection<Long> courseIds) {
		if (courseIds.isEmpty()) {
			return Map.of();
		}
		return courseRepository.findAllById(courseIds).stream()
			.collect(Collectors.toMap(Course::getId, Course::getName));
	}

	private static Specification<Course> searchSpec(long schoolId, String keyword, Long departmentId) {
		String normalizedKeyword = NameNormalizer.normalize(keyword);
		return (root, query, cb) -> {
			List<Predicate> conditions = new ArrayList<>();
			conditions.add(cb.equal(root.get("schoolId"), schoolId));
			conditions.add(cb.equal(root.get("status"), CourseStatus.ACTIVE));
			if (!normalizedKeyword.isEmpty()) {
				conditions.add(cb.like(root.get("normalizedName"), "%" + escapeLike(normalizedKeyword) + "%", '\\'));
			}
			if (departmentId != null) {
				conditions.add(cb.equal(root.get("departmentId"), departmentId));
			}
			return cb.and(conditions.toArray(new Predicate[0]));
		};
	}

	/** 사용자가 친 % 와 _ 가 와일드카드로 먹히지 않게 문자 그대로 찾는다. */
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private Map<Long, DepartmentResponse> departmentsOf(List<Course> courses) {
		return departmentService.findByIds(courses.stream()
			.map(Course::getDepartmentId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet()));
	}

	private static CourseResponse toResponse(Course course, Map<Long, DepartmentResponse> departments, boolean hasMyDisciple) {
		DepartmentResponse department = course.getDepartmentId() == null ? null : departments.get(course.getDepartmentId());
		return new CourseResponse(course.getId(), course.getName(), course.getTerm(),
			department == null ? null : new CourseResponse.Department(department.id(), department.name()),
			hasMyDisciple);
	}

}
