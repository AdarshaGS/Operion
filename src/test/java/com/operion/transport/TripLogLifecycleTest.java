package com.operion.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.operion.communication.CommunicationService;
import com.operion.finance.FeeService;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves TripLog's SCHEDULED -> IN_PROGRESS -> COMPLETED lifecycle, that cancel is
 * blocked once a trip is COMPLETED, and that vehicle/driver are snapshotted at
 * creation (not re-read from the route's current default) - see TripLog's class doc.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, TransportService.class, FeeService.class, TransportNotificationService.class, CommunicationService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TripLogLifecycleTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private RouteRepository routeRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private TransportService transportService;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private GradeLevelRepository gradeLevelRepository;

	@Autowired
	private SchoolClassRepository schoolClassRepository;

	@Autowired
	private SectionRepository sectionRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private StudentEnrollmentRepository studentEnrollmentRepository;

	@Autowired
	private StudentTripAttendanceRepository studentTripAttendanceRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(Route route, Vehicle vehicle, Person driver, Vehicle substituteVehicle, StudentEnrollment enrollment) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Person driver = personRepository.save(new Person("Ramesh", "Kumar"));
		Vehicle vehicle = vehicleRepository.save(new Vehicle(campus, "KA-01-1234", VehicleType.BUS, 40, driver, null));
		Vehicle substituteVehicle = vehicleRepository.save(new Vehicle(campus, "KA-01-5678", VehicleType.BUS, 40, null, null));
		Route route = routeRepository.save(new Route(campus, "Route 12", "R12", vehicle));

		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Person studentPerson = personRepository.save(new Person("Ira", "Shah"));
		Student student = studentRepository.save(new Student(studentPerson, "STU-100", "ADM-100", LocalDate.of(2025, 5, 1),
				null, null, null, null, null, null, null, null, null, null, null, null));
		StudentEnrollment enrollment = studentEnrollmentRepository.save(new StudentEnrollment(student, year, section, 1, LocalDate.of(2025, 6, 1)));

		return new Fixture(route, vehicle, driver, substituteVehicle, enrollment);
	}

	@Test
	void tripProgressesFromScheduledToCompleted() {
		Fixture fixture = setUpFixture("trip-log-lifecycle");

		TripLog trip = transportService.scheduleTrip(fixture.route(), fixture.vehicle(), fixture.driver(), LocalDate.of(2025, 7, 1), TripType.PICKUP);
		assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);

		transportService.startTrip(trip);
		assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
		assertThat(trip.getStartedAt()).isNotNull();

		transportService.completeTrip(trip, "All students dropped");
		assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
		assertThat(trip.getCompletedAt()).isNotNull();
		assertThat(trip.getRemarks()).isEqualTo("All students dropped");
	}

	@Test
	void completingATripThatWasNeverStartedIsRejected() {
		Fixture fixture = setUpFixture("trip-log-complete-without-start");
		TripLog trip = transportService.scheduleTrip(fixture.route(), fixture.vehicle(), fixture.driver(), LocalDate.of(2025, 7, 1), TripType.DROP);

		assertThatThrownBy(() -> transportService.completeTrip(trip, "done")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cancellingACompletedTripIsRejected() {
		Fixture fixture = setUpFixture("trip-log-cancel-after-complete");
		TripLog trip = transportService.scheduleTrip(fixture.route(), fixture.vehicle(), fixture.driver(), LocalDate.of(2025, 7, 1), TripType.PICKUP);
		transportService.startTrip(trip);
		transportService.completeTrip(trip, "done");

		assertThatThrownBy(() -> transportService.cancelTrip(trip, "too late")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cancellingAScheduledTripIsAllowed() {
		Fixture fixture = setUpFixture("trip-log-cancel-scheduled");
		TripLog trip = transportService.scheduleTrip(fixture.route(), fixture.vehicle(), fixture.driver(), LocalDate.of(2025, 7, 1), TripType.PICKUP);

		transportService.cancelTrip(trip, "vehicle breakdown");

		assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELLED);
		assertThat(trip.getRemarks()).isEqualTo("vehicle breakdown");
	}

	@Test
	void tripSnapshotsTheCoveringVehicleIndependentlyOfTheRoutesDefault() {
		Fixture fixture = setUpFixture("trip-log-substitute-vehicle");

		TripLog trip = transportService.scheduleTrip(
				fixture.route(), fixture.substituteVehicle(), null, LocalDate.of(2025, 7, 1), TripType.PICKUP);

		assertThat(trip.getVehicle().getId()).isEqualTo(fixture.substituteVehicle().getId());
		assertThat(trip.getRoute().getVehicle().getId()).isEqualTo(fixture.vehicle().getId());
	}

	@Test
	void markingBoardingInsertsOneRowPerStudent() {
		Fixture fixture = setUpFixture("trip-log-mark-boarding");
		TripLog trip = transportService.scheduleTrip(fixture.route(), fixture.vehicle(), fixture.driver(), LocalDate.of(2025, 7, 1), TripType.PICKUP);

		List<StudentTripAttendance> marked = transportService.markBoarding(trip,
				List.of(new TransportService.BoardingMark(fixture.enrollment(), BoardingStatus.BOARDED, null)));

		assertThat(marked).hasSize(1);
		assertThat(marked.get(0).getBoardingStatus()).isEqualTo(BoardingStatus.BOARDED);
		assertThat(studentTripAttendanceRepository.findByTripLogId(trip.getId())).hasSize(1);
	}

	@Test
	void markingBoardingTwiceForTheSameStudentOnTheSameTripIsRejected() {
		Fixture fixture = setUpFixture("trip-log-mark-boarding-duplicate");
		TripLog trip = transportService.scheduleTrip(fixture.route(), fixture.vehicle(), fixture.driver(), LocalDate.of(2025, 7, 1), TripType.PICKUP);
		transportService.markBoarding(trip, List.of(new TransportService.BoardingMark(fixture.enrollment(), BoardingStatus.BOARDED, null)));

		assertThatThrownBy(() -> transportService.markBoarding(
				trip, List.of(new TransportService.BoardingMark(fixture.enrollment(), BoardingStatus.NOT_BOARDED, null))))
				.isInstanceOf(IllegalStateException.class);
	}
}
