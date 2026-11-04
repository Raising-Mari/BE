package com.aidisciple.domain.auth.service;

import java.time.Clock;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.aidisciple.domain.auth.entity.School;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.global.security.UserRole;

/**
 * 가입 기능이 없어도 과목·제자·배틀을 시험할 수 있게 개발용 계정을 넣는다. 이미 있으면 건드리지 않는다.
 * 모두 가짜 값이며 {@code local} 프로필에서만 실행된다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalSeedUserInitializer implements ApplicationRunner {

	private static final String SCHOOL_CODE = "MJU";

	private record SeedUser(String email, String nickname, UserRole role) {
	}

	private static final List<SeedUser> SEED_USERS = List.of(
		new SeedUser("dev1@mju.ac.kr", "dev_one", UserRole.STUDENT),
		new SeedUser("dev2@mju.ac.kr", "dev_two", UserRole.STUDENT),
		new SeedUser("admin@mju.ac.kr", "dev_admin", UserRole.ADMIN));

	private final UserRepository userRepository;
	private final SchoolRepository schoolRepository;
	private final Clock clock;

	@Override
	public void run(ApplicationArguments args) {
		School school = schoolRepository.findByCode(SCHOOL_CODE).orElse(null);
		if (school == null) {
			log.warn("학교 시드({})가 없어 개발용 계정을 만들지 않습니다. Flyway V1 이 적용됐는지 확인하세요.", SCHOOL_CODE);
			return;
		}
		for (SeedUser seed : SEED_USERS) {
			if (userRepository.findByEmail(seed.email()).isEmpty()) {
				userRepository.save(User.create(school.getId(), null, "dev:" + seed.email(), seed.email(),
					seed.nickname(), seed.role(), clock.instant()));
				log.info("개발용 계정 생성: {}", seed.nickname());
			}
		}
	}

}
