package com.operion.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
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
import com.operion.student.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Extends the tenant-isolation proof from OrganisationTenantIsolationTest /
 * StudentTenantIsolationTest to the Attendance tables.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AttendanceTenantIsolationTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private GradeLevelRepository gradeLevelRepository;

	@Autowired
	private SchoolClassRepository schoolClassRepository;

	@Autowired
	private SectionRepository sectionRepository;

	@Autowired
	private StudentService studentService;

	@Autowired
	private StudentAttendanceRepository studentAttendanceRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private StudentEnrollment enrollInNewOrg(String orgSlug, String admissionNumber) {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", orgSlug));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Person person = personRepository.save(new Person("Test", "Student"));

		Student student = studentService.admit(
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null);
		return studentService.enroll(student, academicYear, section, 1, LocalDate.of(2025, 6, 1));
	}

	@Test
	void queriesOnlySeeTheCurrentTenantsStudentAttendance() {
		StudentEnrollment enrollmentA = enrollInNewOrg("attendance-iso-a-school", "A-100");
		Long orgAId = TenantContext.getOrganisationId();
		studentAttendanceRepository.save(new StudentAttendance(enrollmentA, enrollmentA.getAcademicYear(),
				enrollmentA.getSection().getSchoolClass(), enrollmentA.getSection(), LocalDate.of(2025, 7, 1),
				AttendanceStatus.PRESENT, false, null));

		StudentEnrollment enrollmentB = enrollInNewOrg("attendance-iso-b-school", "B-100");
		studentAttendanceRepository.save(new StudentAttendance(enrollmentB, enrollmentB.getAcademicYear(),
				enrollmentB.getSection().getSchoolClass(), enrollmentB.getSection(), LocalDate.of(2025, 7, 1),
				AttendanceStatus.ABSENT, false, null));

		TenantContext.set(orgAId, null);
		List<StudentAttendance> visibleToA = studentAttendanceRepository.findAll();

		assertThat(visibleToA).hasSize(1);
		assertThat(visibleToA.get(0).getStudentEnrollment().getId()).isEqualTo(enrollmentA.getId());
		assertThat(visibleToA.get(0).getOrganisationId()).isEqualTo(orgAId);
	}
}
