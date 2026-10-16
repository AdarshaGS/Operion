package com.operion.portal.api;

import java.util.List;

import com.operion.common.TenantContext;
import com.operion.modules.AppModule;
import com.operion.modules.RequireModule;
import com.operion.payroll.PayrollService;
import com.operion.payroll.Payslip;
import com.operion.payroll.api.PayslipResponse;
import com.operion.portal.PayslipPortalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No {@code @RequirePermission} - same convention as TeacherPortalController; see PayslipPortalService. */
@RestController
@RequestMapping("/api/v1/me/payslips")
@RequireModule(AppModule.PAYROLL)
public class PayslipPortalController {

	private final PayslipPortalService payslipPortalService;
	private final PayrollService payrollService;

	public PayslipPortalController(PayslipPortalService payslipPortalService, PayrollService payrollService) {
		this.payslipPortalService = payslipPortalService;
		this.payrollService = payrollService;
	}

	@GetMapping
	public List<PayslipResponse> list() {
		return payslipPortalService.payslipsFor(TenantContext.getActorId()).stream()
				.map(slip -> PayslipResponse.from(slip, List.of())).toList();
	}

	@GetMapping("/{id}")
	public PayslipResponse get(@PathVariable Long id) {
		Payslip payslip = payslipPortalService.payslipFor(TenantContext.getActorId(), id);
		return PayslipResponse.from(payslip, payrollService.linesOf(payslip));
	}
}
