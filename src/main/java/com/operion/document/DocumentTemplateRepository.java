package com.operion.document;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {

	Optional<DocumentTemplate> findByDocumentType(DocumentType documentType);
}
