package com.operion.finance.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.imports.ImportRowResult;
import com.operion.finance.FeeCategory;
import com.operion.finance.FeeCategoryRepository;
import com.operion.finance.FeeService;
import com.operion.finance.FeeService.InstallmentInput;
import com.operion.finance.FeeStructure;
import com.operion.finance.FeeStructureGroup;
import com.operion.finance.FeeStructureGroupRepository;
import com.operion.finance.FeeStructureImportService;
import com.operion.finance.FeeStructureInstallmentRepository;
import com.operion.finance.FeeStructureRepository;
import com.operion.finance.PaymentFrequency;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/fees/structures")
@RequirePermission("FEE_VIEW")
public class FeeStructureController {

	private final FeeService feeService;
	private final FeeStructureRepository feeStructureRepository;
	private final FeeStructureInstallmentRepository feeStructureInstallmentRepository;
	private final FeeStructureGroupRepository feeStructureGroupRepository;
	private final FeeCategoryRepository feeCategoryRepository;
	private final FeeStructureImportService feeStructureImportService;

	public FeeStructureController(FeeService feeService, FeeStructureRepository feeStructureRepository,
			FeeStructureInstallmentRepository feeStructureInstallmentRepository, FeeStructureGroupRepository feeStructureGroupRepository,
			FeeCategoryRepository feeCategoryRepository, FeeStructureImportService feeStructureImportService) {
		this.feeService = feeService;
		this.feeStructureRepository = feeStructureRepository;
		this.feeStructureInstallmentRepository = feeStructureInstallmentRepository;
		this.feeStructureGroupRepository = feeStructureGroupRepository;
		this.feeCategoryRepository = feeCategoryRepository;
		this.feeStructureImportService = feeStructureImportService;
	}

	@PostMapping
	@RequirePermission("FEE_STRUCTURE_MANAGE")
	public FeeStructureResponse create(@RequestBody CreateFeeStructureRequest request) {
		FeeStructureGroup feeStructureGroup = feeStructureGroupRepository.findById(request.feeStructureGroupId())
				.orElseThrow(() -> new IllegalArgumentException("No fee structure group with id " + request.feeStructureGroupId()));
		FeeCategory feeCategory = feeCategoryRepository.findById(request.feeCategoryId())
				.orElseThrow(() -> new IllegalArgumentException("No fee category with id " + request.feeCategoryId()));

		List<InstallmentInput> installments = request.installments().stream()
				.map(entry -> new InstallmentInput(entry.installmentNumber(), entry.dueDate(), entry.amount()))
				.toList();

		FeeStructure structure = feeService.createFeeStructure(feeStructureGroup, feeCategory, request.amount(), installments,
				request.paymentFrequency() == null ? PaymentFrequency.MONTHLY : request.paymentFrequency(), request.oneShotDiscountAmount());
		return toResponse(structure);
	}

	@GetMapping
	public List<FeeStructureResponse> list(@RequestParam Long feeStructureGroupId) {
		return feeStructureRepository.findByFeeStructureGroupId(feeStructureGroupId).stream()
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

	/** Bulk setup from a .csv/.xlsx upload - both the FeeStructureGroup and FeeCategory a
	 * row references must already exist (see FeeStructureRowImportService); only the
	 * FeeStructure itself is created. validateOnly=true resolves/checks everything without
	 * persisting, for the upload dialog's preview step. */
	@PostMapping("/import")
	@RequirePermission("FEE_STRUCTURE_MANAGE")
	public List<ImportRowResult> importFile(@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "false") boolean validateOnly) {
		return feeStructureImportService.importFile(file, validateOnly);
	}

	@GetMapping("/export")
	public List<FeeStructureExportResponse> export() {
		return feeStructureRepository.findAll().stream().map(FeeStructureExportResponse::from).toList();
	}
}
