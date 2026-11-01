package com.aidisciple.domain.auth.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.aidisciple.global.security.AuthUser;

/**
 * {@code Authorization: Bearer} 토큰이 유효하면 요청을 로그인 상태로 만든다. 토큰이 없거나 틀려도 여기서 막지 않고
 * 그냥 비로그인으로 넘기며, 거부는 {@code SecurityConfig} 의 경로 규칙이 한다. (공개 경로는 토큰이 틀려도 열려 있어야 한다)
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtProvider jwtProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			jwtProvider.parseAccessToken(header.substring(BEARER_PREFIX.length()).trim())
				.ifPresent(this::authenticate);
		}
		chain.doFilter(request, response);
	}

	private void authenticate(AuthUser user) {
		var authentication = UsernamePasswordAuthenticationToken.authenticated(
			user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
	}

}
