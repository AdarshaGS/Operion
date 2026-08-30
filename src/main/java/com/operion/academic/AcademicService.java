package com.operion.academic;

import java.time.LocalDate;
import java.util.Optional;

import com.operion.identity.Person;
import com.operion.organisation.AcademicYear;
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

	public GradeLevel createGradeLevel(String name, int sequenceOrder, String stage) {
		return gradeLevelRepository.save(new GradeLevel(name, sequenceOrder, stage));
	}

	public GradeLevel changeGradeLevelStatus(GradeLevel gradeLevel, GradeLevelStatus status) {
		gradeLevel.changeStatus(status);
		return gradeLevelRepository.save(gradeLevel);
	}

	public GradeLevel updateGradeLevel(GradeLevel gradeLevel, String name, int sequenceOrder, String stage) {
		gradeLevel.update(name, sequenceOrder, stage);
		return gradeLevelRepository.save(gradeLevel);
	}

	public Subject createSubject(String name, String code) {
		return subjectRepository.save(new Subject(name, code));
	}

	public Subject changeSubjectStatus(Subject subject, SubjectStatus status) {
		subject.changeStatus(status);
		return subjectRepository.save(subject);
	}

	public SchoolClass createSchoolClass(AcademicYear academicYear, Campus campus, GradeLevel gradeLevel, String displayName) {
		return schoolClassRepository.save(new SchoolClass(academicYear, campus, gradeLevel, displayName));
	}

	public SchoolClass changeSchoolClassStatus(SchoolClass schoolClass, SchoolClassStatus status) {
		schoolClass.changeStatus(status);
		return schoolClassRepository.save(schoolClass);
	}

	public SchoolClass updateSchoolClassDisplayName(SchoolClass schoolClass, String displayName) {
		schoolClass.updateDisplayName(displayName);
		return schoolClassRepository.save(schoolClass);
	}

	public Section createSection(SchoolClass schoolClass, String name, Integer capacity, String room) {
		return sectionRepository.save(new Section(schoolClass, name, capacity, room));
	}

	public Section changeSectionStatus(Section section, SectionStatus status) {
		section.changeStatus(status);
		return sectionRepository.save(section);
	}

	public Section updateSection(Section section, String name, Integer capacity, String room) {
		section.update(name, capacity, room);
		return sectionRepository.save(section);
	}

	public ClassSubject assignSubjectToClass(SchoolClass schoolClass, Subject subject, boolean mandatory) {
		return classSubjectRepository.save(new ClassSubject(schoolClass, subject, mandatory));
	}

	public ClassSubject changeClassSubjectStatus(ClassSubject classSubject, ClassSubjectStatus status) {
		classSubject.changeStatus(status);
		return classSubjectRepository.save(classSubject);
	}

	public ClassSubject updateClassSubjectMandatory(ClassSubject classSubject, boolean mandatory) {
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
		assignment.end(endDate);
		return teacherAssignmentRepository.save(assignment);
	}
}
