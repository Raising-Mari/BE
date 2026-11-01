package com.aidisciple.domain.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aidisciple.domain.auth.security.JwtAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

	/**
	 * 필터가 빈이라서 스프링이 서블릿 컨테이너 필터로도 자동 등록한다. 보안 필터 체인 밖에서 한 번 더 도는 것을 막는다.
	 */
	@Bean
	FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(JwtAuthenticationFilter filter) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

}
