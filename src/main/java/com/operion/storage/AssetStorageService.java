package com.operion.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * One seam for binary asset storage (GitHub #25), same "swap the class, not the
 * callers" pattern as {@link com.operion.finance.RazorpayCredentialsProvider}. The
 * reference returned by store() is an opaque string persisted by callers (e.g.
 * OrganisationBranding.logoRef) - never a full URL, so switching the implementation
 * (local disk to S3, say) never invalidates data already saved against the old one.
 */
public interface AssetStorageService {

	/** Validates type/size and persists the file, returning a stable reference. */
	String store(MultipartFile file);

	/** Resolves a previously stored reference back to a URL the frontend can fetch directly. */
	String resolveUrl(String reference);
}
