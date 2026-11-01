package com.aidisciple.domain.auth.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.aidisciple.domain.auth.config.AuthProperties;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/** Google 공개 인증서로 ID 토큰을 확인한다. 인증서는 라이브러리가 캐시한다. Google 토큰은 저장하지 않는다. */
@Slf4j
@Component
public class GoogleApiIdentityVerifier implements GoogleIdentityVerifier {

	private final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
	private final GoogleIdTokenVerifier verifier;

	public GoogleApiIdentityVerifier(AuthProperties properties) {
		if (StringUtils.hasText(properties.googleClientId())) {
			this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), jsonFactory)
				.setAudience(List.of(properties.googleClientId()))
				.build();
		} else {
			this.verifier = null;
			log.warn("GOOGLE_CLIENT_ID 가 비어 있어 Google 로그인을 쓸 수 없습니다. (local 프로필은 dev-login 사용)");
		}
	}

	@Override
	public GoogleIdentity verify(String idToken) {
		if (verifier == null) {
			throw new BusinessException(ErrorCode.INTERNAL_ERROR);
		}
		GoogleIdToken token = parse(idToken);
		try {
			if (!verifier.verify(token)) {
				throw new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
			}
		} catch (GeneralSecurityException e) {
			throw new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
		} catch (IOException e) {
			// 토큰이 아니라 Google 인증서를 가져오지 못한 경우. 사용자 잘못이 아니므로 서버 오류로 다룬다.
			log.error("Google 인증서 조회 실패", e);
			throw new BusinessException(ErrorCode.INTERNAL_ERROR);
		}
		GoogleIdToken.Payload payload = token.getPayload();
		return new GoogleIdentity(payload.getSubject(), payload.getEmail(),
			Boolean.TRUE.equals(payload.getEmailVerified()), payload.getHostedDomain());
	}

	private GoogleIdToken parse(String idToken) {
		try {
			GoogleIdToken token = GoogleIdToken.parse(jsonFactory, idToken);
			if (token == null) {
				throw new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
			}
			return token;
		} catch (IOException | IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
		}
	}

}
