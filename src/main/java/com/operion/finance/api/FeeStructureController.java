package com.operion.finance.api;

import java.util.List;

import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.authorization.RequirePermission;
import com.operion.finance.FeeCategory;
import com.operion.finance.FeeCategoryRepository;
import com.operion.finance.FeeService;
import com.operion.finance.FeeService.InstallmentInput;
import com.operion.finance.FeeStructure;
import com.operion.finance.FeeStructureInstallmentRepository;
import com.operion.finance.FeeStructureRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees/structures")
@RequirePermission("FEE_VIEW")
public class FeeStructureController {

	private final FeeService feeService;
	private final FeeStructureRepository feeStructureRepository;
	private final FeeStructureInstallmentRepository feeStructureInstallmentRepository;
	private final AcademicYearRepository academicYearRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final FeeCategoryRepository feeCategoryRepository;

	public FeeStructureController(FeeService feeService, FeeStructureRepository feeStructureRepository,
			FeeStructureInstallmentRepository feeStructureInstallmentRepository, AcademicYearRepository academicYearRepository,
			SchoolClassRepository schoolClassRepository, FeeCategoryRepository feeCategoryRepository) {
		this.feeService = feeService;
		this.feeStructureRepository = feeStructureRepository;
		this.feeStructureInstallmentRepository = feeStructureInstallmentRepository;
		this.academicYearRepository = academicYearRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.feeCategoryRepository = feeCategoryRepository;
	}

	@PostMapping
	@RequirePermission("FEE_STRUCTURE_MANAGE")
	public FeeStructureResponse create(@RequestBody CreateFeeStructureRequest request) {
		AcademicYear academicYear = academicYearRepository.findById(request.academicYearId())
				.orElseThrow(() -> new IllegalArgumentException("No academic year with id " + request.academicYearId()));
		SchoolClass schoolClass = schoolClassRepository.findById(request.schoolClassId())
				.orElseThrow(() -> new IllegalArgumentException("No school class with id " + request.schoolClassId()));
		FeeCategory feeCategory = feeCategoryRepository.findById(request.feeCategoryId())
				.orElseThrow(() -> new IllegalArgumentException("No fee category with id " + request.feeCategoryId()));

		List<InstallmentInput> installments = request.installments().stream()
				.map(entry -> new InstallmentInput(entry.installmentNumber(), entry.dueDate(), entry.amount()))
				.toList();

		FeeStructure structure = feeService.createFeeStructure(academicYear, schoolClass, feeCategory, request.amount(), installments);
		return toResponse(structure);
	}

	@GetMapping
	public List<FeeStructureResponse> list(@RequestParam Long academicYearId, @RequestParam Long schoolClassId) {
		return feeStructureRepository.findByAcademicYearIdAndSchoolClassId(academicYearId, schoolClassId).stream()
				.map(this::toResponse)
				.toList();
	}

	@GetMapping("/{structureId}")
	public FeeStructureResponse get(@PathVariable Long structureId) {
		FeeStructure structure = feeStructureRepository.findById(structureId)
				.orElseThrow(() -> new IllegalArgumentException("No fee structure with id " + structureId));
		return toResponse(structure);
	}

	private FeeStructureResponse toResponse(FeeStructure structure) {
		return FeeStructureResponse.from(structure, feeStructureInstallmentRepository.findByFeeStructureIdOrderByInstallmentNumber(structure.getId()));
	}
}
