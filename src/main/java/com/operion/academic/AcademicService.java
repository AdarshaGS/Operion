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

	public Subject createSubject(String name, String code, boolean elective) {
		return subjectRepository.save(new Subject(name, code, elective));
	}

	public SchoolClass createSchoolClass(AcademicYear academicYear, Campus campus, GradeLevel gradeLevel, String displayName) {
		return schoolClassRepository.save(new SchoolClass(academicYear, campus, gradeLevel, displayName));
	}

	public Section createSection(SchoolClass schoolClass, String name, Integer capacity, String room) {
		return sectionRepository.save(new Section(schoolClass, name, capacity, room));
	}

	public ClassSubject assignSubjectToClass(SchoolClass schoolClass, Subject subject, boolean mandatory) {
		return classSubjectRepository.save(new ClassSubject(schoolClass, subject, mandatory));
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
}
