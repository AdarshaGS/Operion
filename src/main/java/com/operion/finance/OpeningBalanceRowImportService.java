package com.operion.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.operion.academic.SchoolClass;
import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.organisation.AcademicYear;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * No standalone "opening balance" concept exists in this codebase - modeled instead as a
 * single-installment FeeStructure under the student's own class's existing
 * FeeStructureGroup (which must already be set up, same as FeeStructureRowImportService),
 * tagged with a reserved "Opening Balance" FeeCategory (auto-created once, org-wide -
 * this one category is system-managed, not a user-curated catalog entry, so unlike a
 * regular fee category it's safe to get-or-create). Reuses FeeService.assignFee/
 * generateInvoice unchanged, so outstanding-balance/payment allocation works for free.
 */
@Service
public class OpeningBalanceRowImportService {

	private static final String CATEGORY_CODE = "OPENING_BALANCE";

	private final StudentRepository studentRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final FeeStructureGroupRepository feeStructureGroupRepository;
	private final FeeCategoryRepository feeCategoryRepository;
	private final FeeStructureInstallmentRepository feeStructureInstallmentRepository;
	private final StudentFeeAssignmentRepository studentFeeAssignmentRepository;
	private final FeeService feeService;

	public OpeningBalanceRowImportService(StudentRepository studentRepository, StudentEnrollmentRepository studentEnrollmentRepository,
			FeeStructureGroupRepository feeStructureGroupRepository, FeeCategoryRepository feeCategoryRepository,
			FeeStructureInstallmentRepository feeStructureInstallmentRepository, StudentFeeAssignmentRepository studentFeeAssignmentRepository,
			FeeService feeService) {
		this.studentRepository = studentRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.feeStructureGroupRepository = feeStructureGroupRepository;
		this.feeCategoryRepository = feeCategoryRepository;
		this.feeStructureInstallmentRepository = feeStructureInstallmentRepository;
		this.studentFeeAssignmentRepository = studentFeeAssignmentRepository;
		this.feeService = feeService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		String admissionNumber = require(row, "studentAdmissionNumber");
		Student student = studentRepository.findByAdmissionNumber(admissionNumber)
				.orElseThrow(() -> new IllegalArgumentException("No student with admission number '" + admissionNumber + "'"));

		StudentEnrollment enrollment = studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId())
				.orElseThrow(() -> new IllegalArgumentException("Student '" + admissionNumber + "' has no current enrollment"));

		SchoolClass schoolClass = enrollment.getSection().getSchoolClass();
		AcademicYear academicYear = enrollment.getAcademicYear();

		FeeStructureGroup group = feeStructureGroupRepository.findByAcademicYearIdAndSchoolClassId(academicYear.getId(), schoolClass.getId())
				.stream().findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"No fee structure set up for '" + admissionNumber + "'s class in " + academicYear.getName() + " - set up Fee Structures first"));

		FeeCategory openingBalanceCategory = feeCategoryRepository.findByCodeIgnoreCase(CATEGORY_CODE)
				.orElseGet(() -> feeService.createCategory(CATEGORY_CODE, "Opening Balance", "Legacy dues carried over from bulk import",
						FeeCategoryType.GENERAL));

		boolean duplicate = studentFeeAssignmentRepository
				.findByStudentEnrollmentIdAndStatus(enrollment.getId(), StudentFeeAssignmentStatus.ACTIVE).stream()
				.anyMatch(a -> a.getFeeStructure().getFeeCategory().getId().equals(openingBalanceCategory.getId()));
		if (duplicate) {
			return new ImportRowResult(rowNumber, ImportRowStatus.DUPLICATE, "An opening balance is already recorded for '" + admissionNumber + "'",
					null);
		}

		BigDecimal amount = requireAmount(row, "amount");
		LocalDate asOfDate = requireDate(row, "asOfDate");

		if (validateOnly) {
			return new ImportRowResult(rowNumber, ImportRowStatus.VALID, "Row is valid", null);
		}

		FeeStructure structure = feeService.createFeeStructure(group, openingBalanceCategory, amount,
				List.of(new FeeService.InstallmentInput(1, asOfDate, amount)), PaymentFrequency.ONE_SHOT, null);
		StudentFeeAssignment assignment = feeService.assignFee(enrollment, structure, null, null, null);
		FeeStructureInstallment installment = feeStructureInstallmentRepository
				.findByFeeStructureIdOrderByInstallmentNumber(structure.getId()).get(0);
		feeService.generateInvoice(assignment, installment);

		return new ImportRowResult(rowNumber, ImportRowStatus.IMPORTED, "Created", assignment.getId());
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
