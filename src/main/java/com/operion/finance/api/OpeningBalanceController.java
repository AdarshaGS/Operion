package com.operion.finance.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.imports.ImportRowResult;
import com.operion.finance.FeeCategoryRepository;
import com.operion.finance.OpeningBalanceImportService;
import com.operion.finance.StudentFeeAssignmentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Bulk-only - opening balances have no dedicated create/list UI of their own, see
 * OpeningBalanceRowImportService's javadoc for how this maps onto existing Fee entities. */
@RestController
@RequestMapping("/api/v1/fees/opening-balances")
@RequirePermission("FEE_VIEW")
public class OpeningBalanceController {

	private final OpeningBalanceImportService openingBalanceImportService;
	private final StudentFeeAssignmentRepository studentFeeAssignmentRepository;
	private final FeeCategoryRepository feeCategoryRepository;

	public OpeningBalanceController(OpeningBalanceImportService openingBalanceImportService,
			StudentFeeAssignmentRepository studentFeeAssignmentRepository, FeeCategoryRepository feeCategoryRepository) {
		this.openingBalanceImportService = openingBalanceImportService;
		this.studentFeeAssignmentRepository = studentFeeAssignmentRepository;
		this.feeCategoryRepository = feeCategoryRepository;
	}

	@PostMapping("/import")
	@RequirePermission("FEE_STRUCTURE_MANAGE")
	public List<ImportRowResult> importFile(@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "false") boolean validateOnly) {
		return openingBalanceImportService.importFile(file, validateOnly);
	}

	@GetMapping("/export")
	public List<OpeningBalanceExportResponse> export() {
		return feeCategoryRepository.findByCodeIgnoreCase("OPENING_BALANCE")
				.map(category -> studentFeeAssignmentRepository.findByFeeStructure_FeeCategory_Id(category.getId()).stream()
						.map(OpeningBalanceExportResponse::from).toList())
				.orElseGet(List::of);
	}
}
