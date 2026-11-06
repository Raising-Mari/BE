package com.aidisciple.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminPropertiesTest {

	@Test
	void 목록에_있는_메일만_관리자다() {
		AdminProperties admins = new AdminProperties("a@mju.ac.kr, b@mju.ac.kr");

		assertThat(admins.isAdmin("a@mju.ac.kr")).isTrue();
		assertThat(admins.isAdmin("b@mju.ac.kr")).isTrue();
		assertThat(admins.isAdmin("c@mju.ac.kr")).isFalse();
	}

	@Test
	void 대소문자와_앞뒤_공백은_무시한다() {
		AdminProperties admins = new AdminProperties("  Admin@MJU.ac.kr ");

		assertThat(admins.isAdmin("admin@mju.ac.kr")).isTrue();
		assertThat(admins.isAdmin(" ADMIN@mju.ac.kr ")).isTrue();
	}

	@Test
	void 설정이_비어_있으면_아무도_관리자가_아니다() {
		assertThat(new AdminProperties("").isAdmin("a@mju.ac.kr")).isFalse();
		assertThat(new AdminProperties(" , ").isAdmin("")).isFalse();
		assertThat(new AdminProperties(null).isAdmin("a@mju.ac.kr")).isFalse();
	}

	@Test
	void 부분_일치는_관리자가_아니다() {
		AdminProperties admins = new AdminProperties("a@mju.ac.kr");

		assertThat(admins.isAdmin("aa@mju.ac.kr")).isFalse();
		assertThat(admins.isAdmin("a@mju.ac.kr.evil.com")).isFalse();
		assertThat(admins.isAdmin(null)).isFalse();
	}

}
