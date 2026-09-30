package com.operion.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.operion.academic.TeacherAssignmentRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.hr.EmploymentType;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.payroll.PayrollRun;
import com.operion.payroll.PayrollRunRepository;
import com.operion.payroll.PayrollService;
import com.operion.payroll.Payslip;
import com.operion.payroll.PayslipLineRepository;
import com.operion.payroll.PayslipRepository;
import com.operion.payroll.SalaryComponentRepository;
import com.operion.payroll.SalaryService;
import com.operion.payroll.SalaryStructureLineRepository;
import com.operion.payroll.SalaryStructureRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Staff can only see their own finalized payslips through the portal. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayslipPortalServiceTest {

	@Autowired private SalaryComponentRepository componentRepository;
	@Autowired private SalaryStructureRepository structureRepository;
	@Autowired private SalaryStructureLineRepository structureLineRepository;
	@Autowired private PayrollRunRepository runRepository;
	@Autowired private PayslipRepository payslipRepository;
	@Autowired private PayslipLineRepository payslipLineRepository;
	@Autowired private TeacherAssignmentRepository teacherAssignmentRepository;
	@Autowired private AuditLogRepository auditLogRepository;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private DesignationRepository designationRepository;
	@Autowired private StaffProfileRepository staffProfileRepository;
	@Autowired private OrganisationMembershipRepository membershipRepository;
	@Autowired private RoleRepository roleRepository;
	@Autowired private UserRepository userRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private StaffProfile staff(String first, String code, Designation designation) {
		return staffProfileRepository.save(new StaffProfile(personRepository.save(new Person(first, "Staff")), null, code,
				designation, null, LocalDate.of(2025, 6, 1), EmploymentType.PERMANENT));
	}

	@Test
	void staffSeeOnlyTheirOwnFinalizedPayslips() {
		Organisation organisation = organisationRepository
				.save(new Organisation("Test School", "Test School Trust", "payslip-portal-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		AuditLogService audit = new AuditLogService(auditLogRepository, new ObjectMapper());
		SalaryService salaryService = new SalaryService(componentRepository, structureRepository,
				structureLineRepository, audit);
		PayrollService payrollService = new PayrollService(runRepository, payslipRepository, payslipLineRepository,
				staffProfileRepository, teacherAssignmentRepository, salaryService, audit);
		PayslipPortalService portalService = new PayslipPortalService(membershipRepository, staffProfileRepository,
				payslipRepository, payrollService);

		Designation designation = designationRepository.save(new Designation("Teacher"));
		StaffProfile meera = staff("Meera", "EMP-1", designation);
		StaffProfile other = staff("Other", "EMP-2", designation);
		salaryService.createStructure(meera, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(20000), List.of());
		salaryService.createStructure(other, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(25000), List.of());

		User user = userRepository.save(new User("meera-" + System.nanoTime() + "@example.com", null, "hash"));
		Role role = roleRepository.save(new Role("Staff", "portal access", false, false, true));
		membershipRepository.save(new OrganisationMembership(user, meera.getPerson(), role, null));

		PayrollRun draft = payrollService.generate(YearMonth.of(2026, 3));
		assertThat(portalService.payslipsFor(user.getId())).isEmpty();

		payrollService.finalizeRun(draft);
		List<Payslip> own = portalService.payslipsFor(user.getId());
		assertThat(own).hasSize(1);
		assertThat(own.get(0).getNetPay()).isEqualByComparingTo("20000.00");
		assertThat(portalService.payslipFor(user.getId(), own.get(0).getId()).getEmployeeCode()).isEqualTo("EMP-1");

		Payslip othersSlip = payrollService.payslipsOf(draft).stream()
				.filter(slip -> slip.getEmployeeCode().equals("EMP-2")).findFirst().orElseThrow();
		assertThatThrownBy(() -> portalService.payslipFor(user.getId(), othersSlip.getId()))
				.isInstanceOf(AuthorizationDeniedException.class);

		// Once a correction supersedes the run, the old payslip is no longer visible.
		PayrollRun correction = payrollService.startCorrection(draft);
		payrollService.finalizeRun(correction);
		assertThatThrownBy(() -> portalService.payslipFor(user.getId(), own.get(0).getId()))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThat(portalService.payslipsFor(user.getId())).hasSize(1);
	}
}
