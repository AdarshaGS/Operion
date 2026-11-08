package com.operion.academic;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.operion.identity.Person;
import com.operion.common.InvalidRequestException;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearGuard;
import com.operion.organisation.Campus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns academic structure creation and the one piece of real business logic in this
 * module: history-preserving teacher (re)assignment, per ai-context/erp-system-plan.md
 * §2.1. Everything else is a thin save - duplicate/uniqueness violations are left to
 * the DB constraints, matching how OrganisationService.provision() handles it.
 */
@Service
public class AcademicService {

	private final GradeLevelRepository gradeLevelRepository;
	private final SubjectRepository subjectRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final SectionRepository sectionRepository;
	private final ClassSubjectRepository classSubjectRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;

	public AcademicService(GradeLevelRepository gradeLevelRepository, SubjectRepository subjectRepository,
			SchoolClassRepository schoolClassRepository, SectionRepository sectionRepository,
			ClassSubjectRepository classSubjectRepository, TeacherAssignmentRepository teacherAssignmentRepository) {
		this.gradeLevelRepository = gradeLevelRepository;
		this.subjectRepository = subjectRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.sectionRepository = sectionRepository;
		this.classSubjectRepository = classSubjectRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
	}

	/** Stages a grade level can be grouped under, compared ignoring case, spaces, hyphens and underscores
	 * ("Pre-Primary", "PRE_PRIMARY" and "preprimary" are the same stage). */
	private static final Set<String> GRADE_STAGES = Set.of("nursery", "preprimary", "foundational", "preparatory", "primary",
			"middle", "secondary", "seniorsecondary", "highersecondary");

	private static void requireKnownStage(String stage) {
		if (stage != null && !stage.isBlank() && !GRADE_STAGES.contains(stage.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", ""))) {
			throw new InvalidRequestException("Unknown stage '" + stage + "'; use one of Nursery, Pre-Primary, Foundational, "
					+ "Preparatory, Primary, Middle, Secondary, Senior Secondary or Higher Secondary");
		}
	}

	public GradeLevel createGradeLevel(String name, int sequenceOrder, String stage) {
		requireKnownStage(stage);
		return gradeLevelRepository.save(new GradeLevel(name, sequenceOrder, stage));
	}

	public GradeLevel changeGradeLevelStatus(GradeLevel gradeLevel, GradeLevelStatus status) {
		gradeLevel.changeStatus(status);
		return gradeLevelRepository.save(gradeLevel);
	}

	/** Partial update - a null argument leaves that field unchanged. */
	public GradeLevel updateGradeLevel(GradeLevel gradeLevel, String name, Integer sequenceOrder, String stage) {
		if (name != null && name.isBlank()) {
			throw new InvalidRequestException("Grade level name must not be blank");
		}
		requireKnownStage(stage);
		gradeLevel.update(name != null ? name : gradeLevel.getName(),
				sequenceOrder != null ? sequenceOrder : gradeLevel.getSequenceOrder(),
				stage != null ? stage : gradeLevel.getStage());
		return gradeLevelRepository.save(gradeLevel);
	}

	public Subject createSubject(String name, String code) {
		if (code != null && !code.isBlank() && subjectRepository.findByCodeIgnoreCase(code).isPresent()) {
			throw new IllegalStateException("Subject with code " + code + " already exists");
		}
		return subjectRepository.save(new Subject(name, code));
	}

	public Subject changeSubjectStatus(Subject subject, SubjectStatus status) {
		subject.changeStatus(status);
		return subjectRepository.save(subject);
	}

	public SchoolClass createSchoolClass(AcademicYear academicYear, Campus campus, GradeLevel gradeLevel, String displayName) {
		AcademicYearGuard.requireOpen(academicYear, "adding a class");
		if (gradeLevel.getStatus() != GradeLevelStatus.ACTIVE) {
			throw new IllegalStateException("Grade level " + gradeLevel.getName() + " is not active and cannot be used for a new class");
		}
		return schoolClassRepository.save(new SchoolClass(academicYear, campus, gradeLevel, displayName));
	}

	public SchoolClass changeSchoolClassStatus(SchoolClass schoolClass, SchoolClassStatus status) {
		AcademicStructureYearGuard.requireOpen(schoolClass, "changing a class's status");
		schoolClass.changeStatus(status);
		return schoolClassRepository.save(schoolClass);
	}

	public SchoolClass updateSchoolClassDisplayName(SchoolClass schoolClass, String displayName) {
		AcademicStructureYearGuard.requireOpen(schoolClass, "renaming a class");
		schoolClass.updateDisplayName(displayName);
		return schoolClassRepository.save(schoolClass);
	}

	public Section createSection(SchoolClass schoolClass, String name, Integer capacity, String room) {
		AcademicStructureYearGuard.requireOpen(schoolClass, "adding a section");
		return sectionRepository.save(new Section(schoolClass, name, capacity, room));
	}

	public Section changeSectionStatus(Section section, SectionStatus status) {
		AcademicStructureYearGuard.requireOpen(section, "changing a section's status");
		section.changeStatus(status);
		return sectionRepository.save(section);
	}

	public Section updateSection(Section section, String name, Integer capacity, String room) {
		AcademicStructureYearGuard.requireOpen(section, "editing a section");
		section.update(name, capacity, room);
		return sectionRepository.save(section);
	}

	public ClassSubject assignSubjectToClass(SchoolClass schoolClass, Subject subject, boolean mandatory) {
		AcademicStructureYearGuard.requireOpen(schoolClass, "adding a subject to a class");
		if (subject.getStatus() != SubjectStatus.ACTIVE) {
			throw new IllegalStateException("Subject " + subject.getName() + " is not active and cannot be added to a class");
		}
		return classSubjectRepository.save(new ClassSubject(schoolClass, subject, mandatory));
	}

	public ClassSubject assignSubjectToClass(SchoolClass schoolClass, Subject subject, boolean mandatory, Integer weeklyPeriods) {
		AcademicStructureYearGuard.requireOpen(schoolClass, "adding a subject to a class");
		ClassSubject classSubject = assignSubjectToClass(schoolClass, subject, mandatory);
		if (weeklyPeriods != null) {
			classSubject.updateWeeklyPeriods(weeklyPeriods);
			return classSubjectRepository.save(classSubject);
		}
		return classSubject;
	}

	public ClassSubject updateClassSubject(ClassSubject classSubject, boolean mandatory, Integer weeklyPeriods) {
		AcademicStructureYearGuard.requireOpen(classSubject, "editing a class subject");
		classSubject.updateMandatory(mandatory);
		if (weeklyPeriods != null) {
			classSubject.updateWeeklyPeriods(weeklyPeriods);
		}
		return classSubjectRepository.save(classSubject);
	}

	public ClassSubject changeClassSubjectStatus(ClassSubject classSubject, ClassSubjectStatus status) {
		AcademicStructureYearGuard.requireOpen(classSubject, "changing a class subject's status");
		classSubject.changeStatus(status);
		return classSubjectRepository.save(classSubject);
	}

	public ClassSubject updateClassSubjectMandatory(ClassSubject classSubject, boolean mandatory) {
		AcademicStructureYearGuard.requireOpen(classSubject, "editing a class subject");
		classSubject.updateMandatory(mandatory);
		return classSubjectRepository.save(classSubject);
	}

	/**
	 * Ends any existing ACTIVE assignment for the same (section, subject, type) slot -
	 * subject == null identifies the HOMEROOM slot - then inserts the new row. Never
	 * mutates teacherPerson on an existing row, so "who taught this section when" stays
	 * queryable.
	 */
	@Transactional
	public TeacherAssignment assignTeacher(Section section, Subject subject, Person teacherPerson,
			TeacherAssignmentType assignmentType, LocalDate startDate) {
		AcademicStructureYearGuard.requireOpen(section, "assigning a teacher");
		Optional<TeacherAssignment> existing = subject == null
				? teacherAssignmentRepository.findBySectionIdAndAssignmentTypeAndStatus(
						section.getId(), assignmentType, TeacherAssignmentStatus.ACTIVE)
				: teacherAssignmentRepository.findBySectionIdAndSubjectIdAndAssignmentTypeAndStatus(
						section.getId(), subject.getId(), assignmentType, TeacherAssignmentStatus.ACTIVE);

		existing.ifPresent(assignment -> assignment.end(startDate));

		AcademicYear academicYear = section.getSchoolClass().getAcademicYear();
		return teacherAssignmentRepository.save(
				new TeacherAssignment(academicYear, section, subject, teacherPerson, assignmentType, startDate));
	}

	public TeacherAssignment endTeacherAssignment(TeacherAssignment assignment, LocalDate endDate) {
		AcademicStructureYearGuard.requireOpen(assignment, "ending a teacher assignment");
		assignment.end(endDate);
		return teacherAssignmentRepository.save(assignment);
	}
}
