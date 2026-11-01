package com.aidisciple.global.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.aidisciple.global.error.SecurityErrorHandler;
import com.aidisciple.global.security.UserRole;

/**
 * 보안 필터 체인. 인증 없이 열어야 하는 경로는 이 파일의 PUBLIC_PATHS 한 곳에서만 관리한다.
 * JWT 필터 자체는 {@code domain/auth} 에 있고, {@code global} 이 {@code domain} 을 import 하지 않도록 빈 이름으로 받아 연결만 한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_PATHS = {
		"/api/health",
		"/api/auth/**",
		"/actuator/health",
		"/v3/api-docs/**",
		"/swagger-ui.html",
		"/swagger-ui/**",
	};

	private static final String ADMIN_PATHS = "/api/admin/**";

	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		@Qualifier("jwtAuthenticationFilter") OncePerRequestFilter jwtAuthenticationFilter,
		SecurityErrorHandler errorHandler
	) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint(errorHandler)
				.accessDeniedHandler(errorHandler))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers(PUBLIC_PATHS).permitAll()
				.requestMatchers(ADMIN_PATHS).hasRole(UserRole.ADMIN.name())
				.anyRequest().authenticated());
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

}
