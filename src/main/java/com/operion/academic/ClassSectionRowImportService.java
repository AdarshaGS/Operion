package com.operion.academic;

import java.util.Map;

import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row creates/reuses a GradeLevel, a SchoolClass under a given AcademicYear+Campus,
 * and a Section under that class. Own bean + REQUIRES_NEW per row, same convention and
 * reasoning as StudentRowImportService (a bad row rolls back on its own, without
 * poisoning rows already committed earlier in the same batch).
 */
@Service
public class ClassSectionRowImportService {

	private final AcademicYearRepository academicYearRepository;
	private final CampusRepository campusRepository;
	private final GradeLevelRepository gradeLevelRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final SectionRepository sectionRepository;
	private final AcademicService academicService;

	public ClassSectionRowImportService(AcademicYearRepository academicYearRepository, CampusRepository campusRepository,
			GradeLevelRepository gradeLevelRepository, SchoolClassRepository schoolClassRepository,
			SectionRepository sectionRepository, AcademicService academicService) {
		this.academicYearRepository = academicYearRepository;
		this.campusRepository = campusRepository;
		this.gradeLevelRepository = gradeLevelRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.sectionRepository = sectionRepository;
		this.academicService = academicService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		String academicYearLabel = require(row, "academicYear");
		String campusName = require(row, "campus");
		String gradeLevelName = require(row, "gradeLevel");
		String className = require(row, "className");
		String sectionName = require(row, "sectionName");

		AcademicYear academicYear = academicYearRepository.findByNameIgnoreCase(academicYearLabel)
				.orElseThrow(() -> new IllegalArgumentException("No academic year named " + academicYearLabel));
		Campus campus = campusRepository.findByNameIgnoreCase(campusName)
				.orElseThrow(() -> new IllegalArgumentException("No campus named " + campusName));

		GradeLevel gradeLevel = gradeLevelRepository.findByNameIgnoreCase(gradeLevelName).orElse(null);
		SchoolClass schoolClass = gradeLevel == null ? null : schoolClassRepository
				.findByAcademicYearIdAndCampusIdAndGradeLevelId(academicYear.getId(), campus.getId(), gradeLevel.getId())
				.orElse(null);

		// Duplicate check happens before anything is created, in both modes - only the
		// Section is ever the "duplicate" (GradeLevel/SchoolClass are get-or-create).
		if (schoolClass != null
				&& sectionRepository.findBySchoolClassIdAndNameIgnoreCase(schoolClass.getId(), sectionName).isPresent()) {
			return new ImportRowResult(rowNumber, ImportRowStatus.DUPLICATE, "Section " + sectionName + " already exists", null);
		}

		// gradeSequenceOrder/stage are only required when the grade level doesn't exist
		// yet - an existing grade level ignores those two columns.
		Integer sequenceOrder = null;
		String stage = blankToNull(row.get("stage"));
		if (gradeLevel == null) {
			sequenceOrder = parseInt(require(row, "gradeSequenceOrder"));
		}

		if (validateOnly) {
			return new ImportRowResult(rowNumber, ImportRowStatus.VALID, "Row is valid", null);
		}

		if (gradeLevel == null) {
			gradeLevel = academicService.createGradeLevel(gradeLevelName, sequenceOrder, stage);
		}
		if (schoolClass == null) {
			schoolClass = academicService.createSchoolClass(academicYear, campus, gradeLevel, className);
		}

		Integer capacity = parseIntOrNull(row.get("sectionCapacity"));
		String room = blankToNull(row.get("sectionRoom"));
		Section section = academicService.createSection(schoolClass, sectionName, capacity, room);
		return new ImportRowResult(rowNumber, ImportRowStatus.IMPORTED, "Created", section.getId());
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

	private static int parseInt(String value) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("gradeSequenceOrder must be a number: " + value);
		}
	}

	private static Integer parseIntOrNull(String value) {
		String trimmed = blankToNull(value);
		return trimmed == null ? null : Integer.parseInt(trimmed);
	}
}
