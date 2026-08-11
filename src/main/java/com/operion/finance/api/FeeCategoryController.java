package com.operion.finance.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.finance.FeeCategory;
import com.operion.finance.FeeCategoryRepository;
import com.operion.finance.FeeCategoryStatus;
import com.operion.finance.FeeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees/categories")
@RequirePermission("FEE_VIEW")
public class FeeCategoryController {

	private final FeeService feeService;
	private final FeeCategoryRepository feeCategoryRepository;

	public FeeCategoryController(FeeService feeService, FeeCategoryRepository feeCategoryRepository) {
		this.feeService = feeService;
		this.feeCategoryRepository = feeCategoryRepository;
	}

	@PostMapping
	@RequirePermission("FEE_CATEGORY_MANAGE")
	public FeeCategoryResponse create(@RequestBody CreateFeeCategoryRequest request) {
		FeeCategory category = feeService.createCategory(request.code(), request.name(), request.description());
		return FeeCategoryResponse.from(category);
	}

	@GetMapping
	public List<FeeCategoryResponse> list() {
		return feeCategoryRepository.findByStatus(FeeCategoryStatus.ACTIVE).stream().map(FeeCategoryResponse::from).toList();
	}
}
