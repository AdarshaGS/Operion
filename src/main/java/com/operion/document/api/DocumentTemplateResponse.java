package com.operion.document.api;

import com.operion.document.DocumentTemplate;
import com.operion.document.DocumentType;
import com.operion.document.TemplateStyle;

/** {@code configured=false} means this document type has no saved row yet - the fields
 * are DocumentTemplate's own defaults, not yet persisted for this organisation. */
public record DocumentTemplateResponse(DocumentType documentType, TemplateStyle templateStyle, String pageSize, String fontStyle,
		int fontSize, String headerSubtext, boolean configured) {

	public static DocumentTemplateResponse from(DocumentTemplate template) {
		return new DocumentTemplateResponse(template.getDocumentType(), template.getTemplateStyle(), template.getPageSize(),
				template.getFontStyle(), template.getFontSize(), template.getHeaderSubtext(), true);
	}

	public static DocumentTemplateResponse defaults(DocumentType documentType) {
		DocumentTemplate defaultTemplate = new DocumentTemplate(documentType);
		return new DocumentTemplateResponse(documentType, defaultTemplate.getTemplateStyle(), defaultTemplate.getPageSize(),
				defaultTemplate.getFontStyle(), defaultTemplate.getFontSize(), null, false);
	}
}
