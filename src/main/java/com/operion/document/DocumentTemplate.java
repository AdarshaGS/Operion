package com.operion.document;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A locked branded header/footer frame that a document type (question paper, report
 * card) renders inside, so printed output looks consistent without per-document manual
 * styling. At most one row per (organisation, documentType) - DocumentTemplateController
 * upserts on PUT and falls back to in-memory defaults on GET when a type hasn't been
 * configured yet, so no provisioning-time backfill is needed. Per #31.
 */
@Getter
@Setter
@Entity
@Table(name = "document_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentTemplate extends TenantScopedEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 30)
	private DocumentType documentType;

	@Enumerated(EnumType.STRING)
	@Column(name = "template_style", nullable = false, length = 20)
	private TemplateStyle templateStyle;

	@Column(name = "page_size", nullable = false, length = 20)
	private String pageSize;

	@Column(name = "font_style", nullable = false, length = 50)
	private String fontStyle;

	@Column(name = "font_size", nullable = false)
	private int fontSize;

	@Column(name = "header_subtext")
	private String headerSubtext;

	public DocumentTemplate(DocumentType documentType) {
		this.documentType = documentType;
		this.templateStyle = TemplateStyle.CLASSIC;
		this.pageSize = "A4";
		this.fontStyle = "Sans-serif";
		this.fontSize = 12;
	}
}
