package com.operion.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.operion.attendance.StaffAttendanceRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
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
 * Extends the standing tenant-isolation proof (OrganisationTenantIsolationTest /
 * StudentTenantIsolationTest / AttendanceTenantIsolationTest / FeeTenantIsolationTest /
 * ExaminationTenantIsolationTest / CommunicationTenantIsolationTest /
 * TransportTenantIsolationTest / LibraryTenantIsolationTest /
 * InventoryTenantIsolationTest) to the HR tables.
 *
 * HrService is constructed by hand rather than @Import'd - see StaffAttendanceTest for why.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HrTenantIsolationTest {

	private HrService hrService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private DesignationRepository designationRepository;

	@Autowired
	private StaffProfileRepository staffProfileRepository;

	@Autowired
	private LeaveTypeRepository leaveTypeRepository;

	@Autowired
	private LeaveBalanceRepository leaveBalanceRepository;

	@Autowired
	private LeaveRequestRepository leaveRequestRepository;

	@Autowired
	private StaffDocumentRepository staffDocumentRepository;

	@Autowired
	private JobApplicationRepository jobApplicationRepository;

	@Autowired
	private StaffAssignmentRepository staffAssignmentRepository;

	@Autowired
	private StaffExitRepository staffExitRepository;

	@Autowired
	private StaffBankDetailRepository staffBankDetailRepository;

	@Autowired
	private StaffAttendanceRepository staffAttendanceRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@BeforeEach
	void setUpHrService() {
		hrService = new HrService(staffProfileRepository, leaveTypeRepository, leaveBalanceRepository, leaveRequestRepository,
				staffDocumentRepository, jobApplicationRepository, organisationRepository, staffAssignmentRepository,
				staffExitRepository, staffBankDetailRepository, staffAttendanceRepository,
				new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void queriesOnlySeeTheCurrentTenantsStaffProfiles() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "hr-iso-a-school"));
		TenantContext.set(orgA.getId(), null);
		Campus campusA = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Person personA = personRepository.save(new Person("Org A", "Staff"));
		Designation designationA = designationRepository.save(new Designation("Teacher"));
		hrService.createStaffProfile(personA, campusA, "EMP-A-1", designationA, null, LocalDate.of(2021, 1, 1), EmploymentType.PERMANENT);

		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "hr-iso-b-school"));
		TenantContext.set(orgB.getId(), null);
		Campus campusB = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Person personB = personRepository.save(new Person("Org B", "Staff"));
		Designation designationB = designationRepository.save(new Designation("Teacher"));
		hrService.createStaffProfile(personB, campusB, "EMP-B-1", designationB, null, LocalDate.of(2021, 1, 1), EmploymentType.PERMANENT);

		TenantContext.set(orgA.getId(), null);
		List<StaffProfile> visibleToA = staffProfileRepository.findAll();

		assertThat(visibleToA).hasSize(1);
		assertThat(visibleToA.get(0).getOrganisationId()).isEqualTo(orgA.getId());
		assertThat(visibleToA.get(0).getEmployeeCode()).isEqualTo("EMP-A-1");
	}
}
