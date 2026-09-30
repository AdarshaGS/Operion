package com.operion.payroll.api;

import java.time.YearMonth;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.payroll.PayrollRun;
import com.operion.payroll.PayrollRunRepository;
import com.operion.payroll.PayrollService;
import com.operion.payroll.Payslip;
import com.operion.payroll.PayslipLine;
import com.operion.payroll.PayslipLineRepository;
import com.operion.payroll.PayslipRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequirePermission("PAYROLL_VIEW")
public class PayrollController {

	private final PayrollService payrollService;
	private final PayrollRunRepository runRepository;
	private final PayslipRepository payslipRepository;
	private final PayslipLineRepository lineRepository;

	public PayrollController(PayrollService payrollService, PayrollRunRepository runRepository,
			PayslipRepository payslipRepository, PayslipLineRepository lineRepository) {
		this.payrollService = payrollService;
		this.runRepository = runRepository;
		this.payslipRepository = payslipRepository;
		this.lineRepository = lineRepository;
	}

	@GetMapping("/api/v1/payroll/runs")
	public List<PayrollRunResponse> listRuns() {
		return payrollService.listRuns().stream()
				.map(run -> PayrollRunResponse.from(run, payrollService.payslipsOf(run), false)).toList();
	}

	@GetMapping("/api/v1/payroll/runs/{id}")
	public PayrollRunResponse getRun(@PathVariable Long id) {
		return toDetail(findRun(id));
	}

	@PostMapping("/api/v1/payroll/runs")
	@RequirePermission("PAYROLL_MANAGE")
	public PayrollRunResponse generate(@Valid @RequestBody GeneratePayrollRunRequest request) {
		return toDetail(payrollService.generate(YearMonth.of(request.year(), request.month())));
	}

	@PostMapping("/api/v1/payroll/runs/{id}/finalize")
	@RequirePermission("PAYROLL_MANAGE")
	public PayrollRunResponse finalizeRun(@PathVariable Long id) {
		return toDetail(payrollService.finalizeRun(findRun(id)));
	}

	@PostMapping("/api/v1/payroll/runs/{id}/correct")
	@RequirePermission("PAYROLL_MANAGE")
	public PayrollRunResponse startCorrection(@PathVariable Long id) {
		return toDetail(payrollService.startCorrection(findRun(id)));
	}

	@DeleteMapping("/api/v1/payroll/runs/{id}")
	@RequirePermission("PAYROLL_MANAGE")
	public void discardDraft(@PathVariable Long id) {
		payrollService.discardDraft(findRun(id));
	}

	@GetMapping("/api/v1/payroll/payslips/{id}")
	public PayslipResponse getPayslip(@PathVariable Long id) {
		return toResponse(findPayslip(id));
	}

	@PostMapping("/api/v1/payroll/payslips/{id}/adjustments")
	@RequirePermission("PAYROLL_MANAGE")
	public PayslipResponse addAdjustment(@PathVariable Long id, @Valid @RequestBody AddPayslipAdjustmentRequest request) {
		return toResponse(payrollService.addAdjustment(findPayslip(id), request.label(), request.type(), request.amount()));
	}

	@DeleteMapping("/api/v1/payroll/payslip-lines/{id}")
	@RequirePermission("PAYROLL_MANAGE")
	public PayslipResponse removeAdjustment(@PathVariable Long id) {
		PayslipLine line = lineRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No payslip line with id " + id));
		return toResponse(payrollService.removeAdjustment(line));
	}

	private PayrollRunResponse toDetail(PayrollRun run) {
		return PayrollRunResponse.from(run, payrollService.payslipsOf(run), true);
	}

	private PayslipResponse toResponse(Payslip payslip) {
		return PayslipResponse.from(payslip, payrollService.linesOf(payslip));
	}

	private PayrollRun findRun(Long id) {
		return runRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No payroll run with id " + id));
	}

	private Payslip findPayslip(Long id) {
		return payslipRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No payslip with id " + id));
	}
}
