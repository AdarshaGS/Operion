package com.operion.privacy.api;

/** source: "ADMIN_ENTRY" | "PAPER_FORM" | "VERBAL". granted=false records a withdrawal. */
public record RecordConsentRequest(Long personId, Long purposeId, boolean granted, String source, String note) {
}
