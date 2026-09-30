package com.operion.portal;

import java.util.List;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.payroll.PayrollRunStatus;
import com.operion.payroll.PayrollService;
import com.operion.payroll.Payslip;
import com.operion.payroll.PayslipRepository;
import org.springframework.stereotype.Service;

/**
 * Staff self-service payslips. Never permission-gated (a portal role carries no PAYROLL_*
 * codes): access is "this payslip belongs to the caller's own StaffProfile", checked before any
 * data is returned, and only FINALIZED runs are visible - drafts and superseded payslips are
 * not something staff should see.
 */
@Service
public class PayslipPortalService {

	private final OrganisationMembershipRepository membershipRepository;
	private final StaffProfileRepository staffProfileRepository;
	private final PayslipRepository payslipRepository;
	private final PayrollService payrollService;

	public PayslipPortalService(OrganisationMembershipRepository membershipRepository,
			StaffProfileRepository staffProfileRepository, PayslipRepository payslipRepository,
			PayrollService payrollService) {
		this.membershipRepository = membershipRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.payslipRepository = payslipRepository;
		this.payrollService = payrollService;
	}

	public List<Payslip> payslipsFor(Long userId) {
		return payrollService.finalizedPayslipsFor(requireOwnStaffProfile(userId).getId());
	}

	public Payslip payslipFor(Long userId, Long payslipId) {
		StaffProfile own = requireOwnStaffProfile(userId);
		Payslip payslip = payslipRepository.findById(payslipId)
				.orElseThrow(() -> new AuthorizationDeniedException("No such payslip for this user"));
		if (!payslip.getStaffProfile().getId().equals(own.getId())
				|| payslip.getPayrollRun().getStatus() != PayrollRunStatus.FINALIZED) {
			throw new AuthorizationDeniedException("No such payslip for this user");
		}
		return payslip;
	}

	private StaffProfile requireOwnStaffProfile(Long userId) {
		Long personId = membershipRepository.findByUserId(userId).stream()
				.filter(m -> m.getStatus() == MembershipStatus.ACTIVE).findFirst().map(m -> m.getPerson().getId())
				.orElseThrow(() -> new AuthorizationDeniedException("No active membership for this user"));
		return staffProfileRepository.findByPersonId(personId)
				.orElseThrow(() -> new AuthorizationDeniedException("Caller has no staff profile"));
	}
}
