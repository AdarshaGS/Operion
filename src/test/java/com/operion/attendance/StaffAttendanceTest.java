package com.operion.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
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
 * Proves StaffAttendance is a genuinely separate flow from student attendance - one row
 * per person per day, check-out can't happen twice, per ai-context/erp-system-plan.md §3.1.
 *
 * AttendanceService is constructed by hand rather than @Import'd - see
 * StudentAttendanceLifecycleTest for why.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StaffAttendanceTest {

	private AttendanceService attendanceService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentAttendanceRepository studentAttendanceRepository;

	@Autowired
	private ClassAttendanceRegisterRepository classAttendanceRegisterRepository;

	@Autowired
	private AttendanceCorrectionRepository attendanceCorrectionRepository;

	@Autowired
	private StaffAttendanceRepository staffAttendanceRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@BeforeEach
	void setUpAttendanceService() {
		attendanceService = new AttendanceService(studentAttendanceRepository, classAttendanceRegisterRepository,
				attendanceCorrectionRepository, staffAttendanceRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void checksInAndOutAndRejectsDoubleMarkingTheSameDay() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "staff-attendance-school"));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Person teacher = personRepository.save(new Person("Anil", "Rao"));
		LocalDate date = LocalDate.of(2025, 7, 1);
		Instant checkInTime = Instant.parse("2025-07-01T03:30:00Z");

		StaffAttendance attendance = attendanceService.markStaffAttendance(
				teacher, campus, date, AttendanceStatus.PRESENT, checkInTime, null);
		assertThat(attendance.getCheckOutTime()).isNull();

		assertThatThrownBy(() -> attendanceService.markStaffAttendance(
				teacher, campus, date, AttendanceStatus.PRESENT, checkInTime, null))
				.isInstanceOf(IllegalStateException.class);

		Instant checkOutTime = Instant.parse("2025-07-01T10:00:00Z");
		StaffAttendance checkedOut = attendanceService.checkOutStaff(attendance, checkOutTime);
		assertThat(checkedOut.getCheckOutTime()).isEqualTo(checkOutTime);

		assertThatThrownBy(() -> attendanceService.checkOutStaff(checkedOut, checkOutTime))
				.isInstanceOf(IllegalStateException.class);

		assertThat(staffAttendanceRepository.findByCampusIdAndAttendanceDate(campus.getId(), date)).hasSize(1);
	}
}
