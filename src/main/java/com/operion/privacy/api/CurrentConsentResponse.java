package com.operion.privacy.api;

import java.time.Instant;

import com.operion.privacy.ConsentService.CurrentConsent;

/** state: "GRANTED" | "WITHDRAWN" | "NOT_RECORDED". */
public record CurrentConsentResponse(Long purposeId, String purposeCode, String purposeName, String state, Instant since,
		String source) {

	public static CurrentConsentResponse from(CurrentConsent consent) {
		return new CurrentConsentResponse(consent.purpose().getId(), consent.purpose().getCode(), consent.purpose().getName(),
				consent.state().name(), consent.since(), consent.source() == null ? null : consent.source().name());
	}
}
