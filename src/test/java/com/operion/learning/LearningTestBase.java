package com.operion.learning;

import java.time.LocalDate;
import java.util.List;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Shared fixture for the learning-module tests: one org with two sections of one class, two
 * subjects, a teacher per section/subject, and two enrolled students in section A. Actors are
 * built directly (LearningActor is a plain record) so tests exercise the scope rules without
 * needing memberships.
 */
abstract class LearningTestBase {

	@Autowired protected OrganisationRepository organisationRepository;
	@Autowired protected CampusRepository campusRepository;
	@Autowired protected AcademicYearRepository academicYearRepository;
	@Autowired protected PersonRepository personRepository;
	@Autowired protected GradeLevelRepository gradeLevelRepository;
	@Autowired protected SchoolClassRepository schoolClassRepository;
	@Autowired protected SectionRepository sectionRepository;
	@Autowired protected SubjectRepository subjectRepository;
	@Autowired protected StudentRepository studentRepository;
	@Autowired protected StudentEnrollmentRepository enrollmentRepository;

	protected AcademicYear year;
	protected SchoolClass schoolClass;
	protected Section sectionA;
	protected Section sectionB;
	protected Subject maths;
	protected Subject science;
	protected Person asha;
	protected Person bala;
	protected Student ravi;
	protected Student sita;
	protected StudentEnrollment raviEnrollment;
	protected StudentEnrollment sitaEnrollment;

	protected LearningActor ashaTeachesMathsInA;
	protected LearningActor balaTeachesScienceInB;
	protected LearningActor admin;

	@BeforeEach
	void setUpLearningFixture() {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", "lt-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);

		year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		sectionA = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		sectionB = sectionRepository.save(new Section(schoolClass, "B", 40, null));
		maths = subjectRepository.save(new Subject("Maths", "MATH"));
		science = subjectRepository.save(new Subject("Science", "SCI"));
		asha = personRepository.save(new Person("Asha", "Rao"));
		bala = personRepository.save(new Person("Bala", "Iyer"));
		ravi = newStudent("Ravi", "Kumar", "S-1");
		sita = newStudent("Sita", "Devi", "S-2");
		raviEnrollment = enrollmentRepository.save(new StudentEnrollment(ravi, year, sectionA, 1, LocalDate.of(2025, 6, 2)));
		sitaEnrollment = enrollmentRepository.save(new StudentEnrollment(sita, year, sectionA, 2, LocalDate.of(2025, 6, 2)));

		ashaTeachesMathsInA = new LearningActor(1L, asha.getId(), false, false,
				List.of(new LearningActor.Teaching(sectionA.getId(), maths.getId())));
		balaTeachesScienceInB = new LearningActor(2L, bala.getId(), false, false,
				List.of(new LearningActor.Teaching(sectionB.getId(), science.getId())));
		admin = new LearningActor(3L, asha.getId(), true, true, List.of());
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	protected Student newStudent(String first, String last, String studentId) {
		Person person = personRepository.save(new Person(first, last));
		return studentRepository.save(new Student(person, studentId, "ADM-" + studentId, LocalDate.of(2025, 6, 1), null, null, null, null,
				null, null, null, null, null, null, null, null));
	}

	protected ChildAccessService.OwnChild guardianOf(StudentEnrollment enrollment) {
		return new ChildAccessService.OwnChild(personRepository.save(new Person("Parent", "Of")).getId(), enrollment);
	}
}
