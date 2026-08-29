package com.operion.document.api;

import java.util.List;

/**
 * A template's layout with every DATA_FIELD/QR_CODE/PHOTO element resolved against one
 * student - preview data, not a rendered file (no PDF/image dependency exists in this
 * project yet; see Letter Formats #31 for the same rendering-scope call). TEXT/HEADER_BAND/
 * DIVIDER elements pass through geometry unresolved since they carry no student binding.
 */
public record IdCardRenderResponse(Long templateId, String studentId, double widthMm, double heightMm, List<RenderedElement> elements) {

	/** {@code value} is the resolved text for TEXT/DATA_FIELD/QR_CODE elements (null for
	 * HEADER_BAND/DIVIDER); {@code photoUrl} is set only for PHOTO elements. */
	public record RenderedElement(String id, String type, double x, double y, double width, double height, String value,
			String photoUrl) {
	}
}
