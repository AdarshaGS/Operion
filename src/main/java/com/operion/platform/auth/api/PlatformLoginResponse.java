package com.operion.platform.auth.api;

import java.time.Instant;

import com.operion.platform.auth.PlatformAuthenticationService.PlatformLoginResult;

public record PlatformLoginResponse(String token, Instant expiresAt, Long platformAdminId) {

	static PlatformLoginResponse from(PlatformLoginResult result) {
		return new PlatformLoginResponse(result.token(), result.expiresAt(), result.platformAdminId());
	}
}
