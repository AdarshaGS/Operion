package com.operion.document.api;

import com.operion.authorization.RequirePermission;
import com.operion.document.DocumentTemplate;
import com.operion.document.DocumentTemplateRepository;
import com.operion.document.DocumentType;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET is open to any authenticated org member (same reasoning as
 * OrganisationBrandingController.get() - a picker/preview endpoint), and returns
 * in-memory defaults rather than 404ing when the org hasn't configured this document
 * type yet. PUT upserts, since a DocumentTemplate row is created lazily on first save
 * rather than backfilled at provisioning time. Per #31.
 */
@RestController
@RequestMapping("/api/v1/document-templates")
public class DocumentTemplateController {

	private final DocumentTemplateRepository documentTemplateRepository;

	public DocumentTemplateController(DocumentTemplateRepository documentTemplateRepository) {
		this.documentTemplateRepository = documentTemplateRepository;
	}

	@GetMapping("/{documentType}")
	public DocumentTemplateResponse get(@PathVariable DocumentType documentType) {
		return documentTemplateRepository.findByDocumentType(documentType)
				.map(DocumentTemplateResponse::from)
				.orElseGet(() -> DocumentTemplateResponse.defaults(documentType));
	}

	@PutMapping("/{documentType}")
	@RequirePermission("ORGANISATION_MANAGE")
	public DocumentTemplateResponse update(@PathVariable DocumentType documentType, @Valid @RequestBody UpsertDocumentTemplateRequest request) {
		DocumentTemplate template = documentTemplateRepository.findByDocumentType(documentType)
				.orElseGet(() -> new DocumentTemplate(documentType));
		template.setTemplateStyle(request.templateStyle());
		template.setPageSize(request.pageSize());
		template.setFontStyle(request.fontStyle());
		template.setFontSize(request.fontSize());
		template.setHeaderSubtext(request.headerSubtext());
		return DocumentTemplateResponse.from(documentTemplateRepository.save(template));
	}
}
