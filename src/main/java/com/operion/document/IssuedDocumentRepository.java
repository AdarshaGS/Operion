package com.operion.document;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuedDocumentRepository extends JpaRepository<IssuedDocument, Long> {

	Optional<IssuedDocument> findFirstByDocumentKindAndSubjectTypeAndSubjectIdOrderByIdDesc(IssuedDocumentKind documentKind,
			String subjectType, Long subjectId);
}
