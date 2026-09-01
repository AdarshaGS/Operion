package com.operion.finance.api;

import java.util.List;

import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.authorization.RequirePermission;
import com.operion.finance.FeeService;
import com.operion.finance.FeeStructureGroup;
import com.operion.finance.FeeStructureGroupRepository;
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
@RequestMapping("/api/v1/fees/structure-groups")
@RequirePermission("FEE_VIEW")
public class FeeStructureGroupController {

	private final FeeService feeService;
	private final FeeStructureGroupRepository feeStructureGroupRepository;
	private final AcademicYearRepository academicYearRepository;
	private final SchoolClassRepository schoolClassRepository;

	public FeeStructureGroupController(FeeService feeService, FeeStructureGroupRepository feeStructureGroupRepository,
			AcademicYearRepository academicYearRepository, SchoolClassRepository schoolClassRepository) {
		this.feeService = feeService;
		this.feeStructureGroupRepository = feeStructureGroupRepository;
		this.academicYearRepository = academicYearRepository;
		this.schoolClassRepository = schoolClassRepository;
	}

	@PostMapping
	@RequirePermission("FEE_STRUCTURE_MANAGE")
	public FeeStructureGroupResponse create(@RequestBody CreateFeeStructureGroupRequest request) {
		AcademicYear academicYear = academicYearRepository.findById(request.academicYearId())
				.orElseThrow(() -> new IllegalArgumentException("No academic year with id " + request.academicYearId()));
		SchoolClass schoolClass = schoolClassRepository.findById(request.schoolClassId())
				.orElseThrow(() -> new IllegalArgumentException("No school class with id " + request.schoolClassId()));

		FeeStructureGroup group = feeService.createFeeStructureGroup(request.name(), academicYear, schoolClass);
		return FeeStructureGroupResponse.from(group);
	}

	@GetMapping
	public List<FeeStructureGroupResponse> list(@RequestParam Long academicYearId, @RequestParam Long schoolClassId) {
		return feeStructureGroupRepository.findByAcademicYearIdAndSchoolClassId(academicYearId, schoolClassId).stream()
				.map(FeeStructureGroupResponse::from)
				.toList();
	}

	@GetMapping("/{groupId}")
	public FeeStructureGroupResponse get(@PathVariable Long groupId) {
		FeeStructureGroup group = feeStructureGroupRepository.findById(groupId)
				.orElseThrow(() -> new IllegalArgumentException("No fee structure group with id " + groupId));
		return FeeStructureGroupResponse.from(group);
	}
}
