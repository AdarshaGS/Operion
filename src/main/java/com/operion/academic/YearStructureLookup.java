package com.operion.academic;

import java.util.Optional;

import com.operion.organisation.AcademicYear;
import org.springframework.stereotype.Component;

/**
 * Finds the counterpart of a class or section in another academic year. A class is the same
 * class when campus and grade match, a section when its class does and its name does - the
 * only identity that survives a year change, since each year has its own rows.
 */
@Component
public class YearStructureLookup {

	private final SchoolClassRepository classRepository;
	private final SectionRepository sectionRepository;

	public YearStructureLookup(SchoolClassRepository classRepository, SectionRepository sectionRepository) {
		this.classRepository = classRepository;
		this.sectionRepository = sectionRepository;
	}

	public Optional<SchoolClass> classIn(AcademicYear year, SchoolClass sourceClass) {
		return classRepository.findByAcademicYearIdAndCampusIdAndGradeLevelId(
				year.getId(), sourceClass.getCampus().getId(), sourceClass.getGradeLevel().getId());
	}

	public Optional<Section> sectionIn(AcademicYear year, Section sourceSection) {
		return classIn(year, sourceSection.getSchoolClass())
				.flatMap(targetClass -> sectionRepository.findBySchoolClassIdAndNameIgnoreCase(targetClass.getId(), sourceSection.getName()));
	}
}
