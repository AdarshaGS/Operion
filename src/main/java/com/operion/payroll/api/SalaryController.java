package com.operion.payroll.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.payroll.SalaryComponent;
import com.operion.payroll.SalaryComponentRepository;
import com.operion.payroll.SalaryService;
import com.operion.payroll.SalaryStructure;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequirePermission("PAYROLL_VIEW")
public class SalaryController {

	private final SalaryService salaryService;
	private final SalaryComponentRepository componentRepository;
	private final StaffProfileRepository staffProfileRepository;

	public SalaryController(SalaryService salaryService, SalaryComponentRepository componentRepository,
			StaffProfileRepository staffProfileRepository) {
		this.salaryService = salaryService;
		this.componentRepository = componentRepository;
		this.staffProfileRepository = staffProfileRepository;
	}

	@GetMapping("/api/v1/payroll/components")
	public List<SalaryComponentResponse> listComponents() {
		return salaryService.listComponents().stream().map(SalaryComponentResponse::from).toList();
	}

	@PostMapping("/api/v1/payroll/components")
	@RequirePermission("PAYROLL_MANAGE")
	public SalaryComponentResponse createComponent(@Valid @RequestBody CreateSalaryComponentRequest request) {
		return SalaryComponentResponse
				.from(salaryService.createComponent(request.name(), request.type(), request.calculationBasis()));
	}

	@PostMapping("/api/v1/payroll/components/{id}/status")
	@RequirePermission("PAYROLL_MANAGE")
	public SalaryComponentResponse changeComponentStatus(@PathVariable Long id,
			@Valid @RequestBody ChangeSalaryComponentStatusRequest request) {
		return SalaryComponentResponse.from(salaryService.changeComponentStatus(findComponent(id), request.status()));
	}

	/** Newest first; the first row is the structure currently (or next) in force. */
	@GetMapping("/api/v1/hr/staff/{staffId}/salary-structures")
	public List<SalaryStructureResponse> history(@PathVariable Long staffId) {
		findStaff(staffId);
		return salaryService.historyFor(staffId).stream().map(this::toResponse).toList();
	}

	@PostMapping("/api/v1/hr/staff/{staffId}/salary-structures")
	@RequirePermission("PAYROLL_MANAGE")
	public SalaryStructureResponse createStructure(@PathVariable Long staffId,
			@Valid @RequestBody CreateSalaryStructureRequest request) {
		StaffProfile staff = findStaff(staffId);
		List<CreateSalaryStructureRequest.Line> requested = request.lines() == null ? List.of() : request.lines();
		List<SalaryService.LineInput> lines = requested.stream()
				.map(line -> new SalaryService.LineInput(findComponent(line.componentId()), line.value())).toList();
		return toResponse(salaryService.createStructure(staff, request.effectiveFrom(), request.basicAmount(), lines));
	}

	private SalaryStructureResponse toResponse(SalaryStructure structure) {
		return SalaryStructureResponse.from(structure, salaryService.linesOf(structure));
	}

	private StaffProfile findStaff(Long id) {
		return staffProfileRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No staff member with id " + id));
	}

	private SalaryComponent findComponent(Long id) {
		return componentRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No salary component with id " + id));
	}
}
