package com.operion.academic;

import java.util.Map;

import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** One row, one transaction - same REQUIRES_NEW convention as StudentRowImportService. */
@Service
public class SubjectRowImportService {

	private final SubjectRepository subjectRepository;
	private final AcademicService academicService;

	public SubjectRowImportService(SubjectRepository subjectRepository, AcademicService academicService) {
		this.subjectRepository = subjectRepository;
		this.academicService = academicService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		String name = require(row, "name");
		String code = blankToNull(row.get("code"));

		if (code != null && subjectRepository.findByCodeIgnoreCase(code).isPresent()) {
			return new ImportRowResult(rowNumber, ImportRowStatus.DUPLICATE, "Subject with code " + code + " already exists", null);
		}

		if (validateOnly) {
			return new ImportRowResult(rowNumber, ImportRowStatus.VALID, "Row is valid", null);
		}

		Subject subject = academicService.createSubject(name, code);
		return new ImportRowResult(rowNumber, ImportRowStatus.IMPORTED, "Created", subject.getId());
	}

	private static String require(Map<String, String> row, String field) {
		String value = blankToNull(row.get(field));
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
