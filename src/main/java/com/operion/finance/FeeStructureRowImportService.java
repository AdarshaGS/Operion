package com.operion.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row, one transaction - see StudentRowImportService's javadoc for why this must be
 * a separate bean from FeeStructureImportService. Both the FeeStructureGroup and the
 * FeeCategory a row references must already exist (set up deliberately via Fee Structures
 * setup, not fabricated here) - only the FeeStructure itself is created. Deliberate scope
 * cut: one installment per structure (amount due in full on installmentDueDate) - a
 * multi-installment payment schedule import is a separate, bigger feature.
 */
@Service
public class FeeStructureRowImportService {

	private final AcademicYearRepository academicYearRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final FeeStructureGroupRepository feeStructureGroupRepository;
	private final FeeCategoryRepository feeCategoryRepository;
	private final FeeStructureRepository feeStructureRepository;
	private final FeeService feeService;

	public FeeStructureRowImportService(AcademicYearRepository academicYearRepository, SchoolClassRepository schoolClassRepository,
			FeeStructureGroupRepository feeStructureGroupRepository, FeeCategoryRepository feeCategoryRepository,
			FeeStructureRepository feeStructureRepository, FeeService feeService) {
		this.academicYearRepository = academicYearRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.feeStructureGroupRepository = feeStructureGroupRepository;
		this.feeCategoryRepository = feeCategoryRepository;
		this.feeStructureRepository = feeStructureRepository;
		this.feeService = feeService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		String academicYearName = require(row, "academicYear");
		AcademicYear academicYear = academicYearRepository.findByNameIgnoreCase(academicYearName)
				.orElseThrow(() -> new IllegalArgumentException("Unknown academic year '" + academicYearName + "'"));

		String className = require(row, "className");
		SchoolClass schoolClass = resolveSchoolClass(academicYear, className);

		String categoryName = require(row, "feeCategory");
		FeeCategory feeCategory = feeCategoryRepository.findByNameIgnoreCase(categoryName)
				.orElseThrow(() -> new IllegalArgumentException("Unknown fee category '" + categoryName + "' - set it up first"));

		FeeStructureGroup group = feeStructureGroupRepository.findByAcademicYearIdAndSchoolClassId(academicYear.getId(), schoolClass.getId())
				.stream().findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"No fee structure group set up for class '" + className + "' in " + academicYearName + " - set up Fee Structures first"));

		boolean duplicate = feeStructureRepository.findByFeeStructureGroupId(group.getId()).stream()
				.anyMatch(fs -> fs.getFeeCategory().getId().equals(feeCategory.getId()));
		if (duplicate) {
			return new ImportRowResult(rowNumber, ImportRowStatus.DUPLICATE,
					"A fee structure for '" + categoryName + "' already exists in this group", null);
		}

		BigDecimal amount = requireAmount(row, "amount");
		LocalDate dueDate = requireDate(row, "installmentDueDate");
		PaymentFrequency paymentFrequency = parsePaymentFrequency(row.get("paymentFrequency"));

		if (validateOnly) {
			return new ImportRowResult(rowNumber, ImportRowStatus.VALID, "Row is valid", null);
		}

		FeeStructure structure = feeService.createFeeStructure(group, feeCategory, amount,
				List.of(new FeeService.InstallmentInput(1, dueDate, amount)), paymentFrequency, null);
		return new ImportRowResult(rowNumber, ImportRowStatus.IMPORTED, "Created", structure.getId());
	}

	private SchoolClass resolveSchoolClass(AcademicYear academicYear, String className) {
		return schoolClassRepository.findByAcademicYearId(academicYear.getId()).stream()
				.filter(sc -> className.equalsIgnoreCase(sc.getDisplayName() != null ? sc.getDisplayName() : sc.getGradeLevel().getName()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown class '" + className + "' in " + academicYear.getName()));
	}

	private static PaymentFrequency parsePaymentFrequency(String value) {
		String trimmed = blankToNull(value);
		if (trimmed == null) {
			return PaymentFrequency.MONTHLY;
		}
		try {
			return PaymentFrequency.valueOf(trimmed.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unknown paymentFrequency '" + trimmed + "'");
		}
	}

	private static String require(Map<String, String> row, String field) {
		String value = blankToNull(row.get(field));
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private static BigDecimal requireAmount(Map<String, String> row, String field) {
		String value = require(row, field);
		try {
			return new BigDecimal(value.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(field + " '" + value + "' is not a valid amount");
		}
	}

	private static LocalDate requireDate(Map<String, String> row, String field) {
		String value = require(row, field);
		try {
			return LocalDate.parse(value.trim());
		} catch (Exception e) {
			throw new IllegalArgumentException(field + " '" + value + "' is not a valid date (expected yyyy-MM-dd)");
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
