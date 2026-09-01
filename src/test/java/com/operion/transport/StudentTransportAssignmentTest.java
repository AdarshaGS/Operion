package com.operion.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
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
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.finance.FeeCategory;
import com.operion.finance.FeeService;
import com.operion.finance.FeeStructure;
import com.operion.finance.FeeStructureGroup;
import com.operion.student.Student;
import com.operion.student.StudentDocumentRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentIdGenerator;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves TransportService's assignment rules: one ACTIVE StudentTransportAssignment
 * per StudentEnrollment (same convention as StudentEnrollment.is_current /
 * StudentGuardian.is_primary_guardian), route/stop must belong to the student's own
 * campus, mid-year route reassignment mutates in place, and ending frees the
 * enrollment up for a fresh assignment.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, TransportService.class, FeeService.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentTransportAssignmentTest {

	private StudentService studentService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private GradeLevelRepository gradeLevelRepository;

	@Autowired
	private SchoolClassRepository schoolClassRepository;

	@Autowired
	private SectionRepository sectionRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private StudentEnrollmentRepository studentEnrollmentRepository;

	@Autowired
	private StudentDocumentRepository studentDocumentRepository;

	@Autowired
	private StudentExitRepository studentExitRepository;

	@Autowired
	private StudentIdGenerator studentIdGenerator;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private TransportService transportService;

	@Autowired
	private RouteRepository routeRepository;

	@Autowired
	private StudentTransportAssignmentRepository studentTransportAssignmentRepository;

	@Autowired
	private FeeService feeService;

	@Autowired
	private RouteStopRepository routeStopRepository;

	@BeforeEach
	void setUpStudentService() {
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
				studentExitRepository, null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(
			StudentEnrollment enrollment, Route route, RouteStop stop, Campus otherCampus, AcademicYear year, SchoolClass schoolClass) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Campus otherCampus = campusRepository.save(new Campus("North Campus", "NORTH"));
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));

		Person person = personRepository.save(new Person("Ira", "Shah"));
		Student student = studentService.admit(person, "ADM-100", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null);
		StudentEnrollment enrollment = studentService.enroll(student, year, section, 1, LocalDate.of(2025, 6, 1));

		Route route = routeRepository.save(new Route(campus, "Route 12", "R12", null));
		RouteStop stop = routeStopRepository.save(new RouteStop(route, "Gate 1", 1, null, null, null, null));

		return new Fixture(enrollment, route, stop, otherCampus, year, schoolClass);
	}

	@Test
	void assigningStudentCreatesAnActiveAssignment() {
		Fixture fixture = setUpFixture("transport-assign-basic");

		StudentTransportAssignment assignment = transportService.assignStudent(
				fixture.enrollment(), fixture.route(), fixture.stop(), true, true, LocalDate.of(2025, 6, 1));

		assertThat(assignment.getStatus()).isEqualTo(TransportAssignmentStatus.ACTIVE);
		assertThat(assignment.isUsesPickup()).isTrue();
		assertThat(assignment.isUsesDrop()).isTrue();
	}

	@Test
	void secondActiveAssignmentForTheSameEnrollmentIsRejected() {
		Fixture fixture = setUpFixture("transport-assign-duplicate");
		transportService.assignStudent(fixture.enrollment(), fixture.route(), fixture.stop(), true, true, LocalDate.of(2025, 6, 1));

		assertThatThrownBy(() ->
				transportService.assignStudent(fixture.enrollment(), fixture.route(), fixture.stop(), true, false, LocalDate.of(2025, 6, 1)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void routeFromADifferentCampusIsRejected() {
		Fixture fixture = setUpFixture("transport-assign-cross-campus");
		Route otherCampusRoute = routeRepository.save(new Route(fixture.otherCampus(), "Route 9", "R9", null));
		RouteStop otherCampusStop = routeStopRepository.save(new RouteStop(otherCampusRoute, "Gate X", 1, null, null, null, null));

		assertThatThrownBy(() -> transportService.assignStudent(
				fixture.enrollment(), otherCampusRoute, otherCampusStop, true, true, LocalDate.of(2025, 6, 1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void stopMustBelongToTheGivenRoute() {
		Fixture fixture = setUpFixture("transport-assign-stop-mismatch");
		Route anotherRoute = routeRepository.save(new Route(fixture.route().getCampus(), "Route 7", "R7", null));

		assertThatThrownBy(() -> transportService.assignStudent(
				fixture.enrollment(), anotherRoute, fixture.stop(), true, true, LocalDate.of(2025, 6, 1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void reassignRouteEndsTheOldRowAndInsertsANewOne() {
		Fixture fixture = setUpFixture("transport-assign-reassign");
		StudentTransportAssignment assignment = transportService.assignStudent(
				fixture.enrollment(), fixture.route(), fixture.stop(), true, true, LocalDate.of(2025, 6, 1));

		RouteStop newStop = routeStopRepository.save(new RouteStop(fixture.route(), "Gate 2", 2, null, null, null, null));
		StudentTransportAssignment reassigned = transportService.reassignRoute(
				assignment, fixture.route(), newStop, LocalDate.of(2025, 9, 1));

		assertThat(reassigned.getId()).isNotEqualTo(assignment.getId());
		assertThat(reassigned.getStatus()).isEqualTo(TransportAssignmentStatus.ACTIVE);
		assertThat(reassigned.getRouteStop().getId()).isEqualTo(newStop.getId());
		assertThat(reassigned.getEffectiveFrom()).isEqualTo(LocalDate.of(2025, 9, 1));

		StudentTransportAssignment original = studentTransportAssignmentRepository.findById(assignment.getId()).orElseThrow();
		assertThat(original.getStatus()).isEqualTo(TransportAssignmentStatus.ENDED);
		assertThat(original.getEffectiveTo()).isEqualTo(LocalDate.of(2025, 9, 1));
		assertThat(original.getRouteStop().getId()).isEqualTo(fixture.stop().getId());
	}

	@Test
	void reassignRouteToAFullVehicleIsRejected() {
		Fixture fixture = setUpFixture("transport-reassign-capacity");
		Vehicle vehicleA = transportService.createVehicle(fixture.route().getCampus(), "KA-01-AB-5678", VehicleType.BUS, 1, null, null);
		transportService.assignVehicleToRoute(fixture.route(), vehicleA);
		transportService.assignStudent(fixture.enrollment(), fixture.route(), fixture.stop(), true, true, LocalDate.of(2025, 6, 1));

		Vehicle vehicleB = transportService.createVehicle(fixture.route().getCampus(), "KA-01-AB-9999", VehicleType.BUS, 1, null, null);
		Route routeB = routeRepository.save(new Route(fixture.route().getCampus(), "Route 20", "R20", vehicleB));
		RouteStop stopB = routeStopRepository.save(new RouteStop(routeB, "Gate 3", 1, null, null, null, null));

		Person otherPerson = personRepository.save(new Person("Ada", "Iyer"));
		Student otherStudent = studentService.admit(otherPerson, "ADM-102", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null);
		StudentEnrollment otherEnrollment = studentService.enroll(otherStudent, academicYearRepository.findAll().get(0),
				sectionRepository.findAll().get(0), 3, LocalDate.of(2025, 6, 1));
		StudentTransportAssignment otherAssignment = transportService.assignStudent(
				otherEnrollment, routeB, stopB, true, true, LocalDate.of(2025, 6, 1));

		assertThatThrownBy(() -> transportService.reassignRoute(otherAssignment, fixture.route(), fixture.stop(), LocalDate.of(2025, 9, 1)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void reassigningWithinTheSameVehicleDoesNotDoubleCountTheStudentsOwnSeat() {
		Fixture fixture = setUpFixture("transport-reassign-same-vehicle");
		Vehicle vehicle = transportService.createVehicle(fixture.route().getCampus(), "KA-01-AB-1111", VehicleType.BUS, 1, null, null);
		transportService.assignVehicleToRoute(fixture.route(), vehicle);
		StudentTransportAssignment assignment = transportService.assignStudent(
				fixture.enrollment(), fixture.route(), fixture.stop(), true, true, LocalDate.of(2025, 6, 1));

		RouteStop newStop = routeStopRepository.save(new RouteStop(fixture.route(), "Gate 2", 2, null, null, null, null));
		StudentTransportAssignment reassigned = transportService.reassignRoute(
				assignment, fixture.route(), newStop, LocalDate.of(2025, 9, 1));

		assertThat(reassigned.getStatus()).isEqualTo(TransportAssignmentStatus.ACTIVE);
	}

	@Test
	void endingAssignmentFreesTheEnrollmentForAFreshOne() {
		Fixture fixture = setUpFixture("transport-assign-end-then-reassign");
		StudentTransportAssignment assignment = transportService.assignStudent(
				fixture.enrollment(), fixture.route(), fixture.stop(), true, true, LocalDate.of(2025, 6, 1));

		transportService.endAssignment(assignment, LocalDate.of(2025, 12, 1));

		StudentTransportAssignment fresh = transportService.assignStudent(
				fixture.enrollment(), fixture.route(), fixture.stop(), false, true, LocalDate.of(2025, 12, 2));

		assertThat(fresh.getStatus()).isEqualTo(TransportAssignmentStatus.ACTIVE);
		assertThat(assignment.getStatus()).isEqualTo(TransportAssignmentStatus.ENDED);
	}

	@Test
	void assignmentBeyondVehicleCapacityIsRejected() {
		Fixture fixture = setUpFixture("transport-assign-capacity");
		Vehicle vehicle = transportService.createVehicle(fixture.route().getCampus(), "KA-01-AB-1234", VehicleType.BUS, 1, null, null);
		transportService.assignVehicleToRoute(fixture.route(), vehicle);
		transportService.assignStudent(fixture.enrollment(), fixture.route(), fixture.stop(), true, true, LocalDate.of(2025, 6, 1));

		Person otherPerson = personRepository.save(new Person("Vir", "Rao"));
		Student otherStudent = studentService.admit(otherPerson, "ADM-101", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null);
		StudentEnrollment otherEnrollment = studentService.enroll(otherStudent, academicYearRepository.findAll().get(0),
				sectionRepository.findAll().get(0), 2, LocalDate.of(2025, 6, 1));

		assertThatThrownBy(() -> transportService.assignStudent(
				otherEnrollment, fixture.route(), fixture.stop(), true, true, LocalDate.of(2025, 6, 1)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void atLeastOnePickupOrDropLegIsRequired() {
		Fixture fixture = setUpFixture("transport-assign-no-legs");

		assertThatThrownBy(() -> transportService.assignStudent(
				fixture.enrollment(), fixture.route(), fixture.stop(), false, false, LocalDate.of(2025, 6, 1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void assigningWithAFeeStructureLinksAStudentFeeAssignment() {
		Fixture fixture = setUpFixture("transport-assign-with-fee");
		FeeStructureGroup feeStructureGroup = feeService.createFeeStructureGroup("Grade 5 Annual Fees 2025-26", fixture.year(), fixture.schoolClass());
		FeeCategory category = feeService.createCategory("TRANSPORT", "Transport", null);
		FeeStructure feeStructure = feeService.createFeeStructure(feeStructureGroup, category,
				new BigDecimal("500.00"), List.of(new FeeService.InstallmentInput(1, LocalDate.of(2025, 7, 1), new BigDecimal("500.00"))));

		StudentTransportAssignment assignment = transportService.assignStudent(
				fixture.enrollment(), fixture.route(), fixture.stop(), true, true, LocalDate.of(2025, 6, 1), feeStructure);

		assertThat(assignment.getStudentFeeAssignment()).isNotNull();
		assertThat(assignment.getStudentFeeAssignment().getFeeStructure().getId()).isEqualTo(feeStructure.getId());
		assertThat(assignment.getStudentFeeAssignment().getEffectiveAmount()).isEqualByComparingTo("500.00");
	}

	@Test
	void assigningWithoutAFeeStructureLeavesTheLinkEmpty() {
		Fixture fixture = setUpFixture("transport-assign-without-fee");

		StudentTransportAssignment assignment = transportService.assignStudent(
				fixture.enrollment(), fixture.route(), fixture.stop(), true, true, LocalDate.of(2025, 6, 1));

		assertThat(assignment.getStudentFeeAssignment()).isNull();
	}
}
