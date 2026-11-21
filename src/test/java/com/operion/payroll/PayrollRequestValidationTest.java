package com.operion.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.operion.payroll.api.AddPayslipAdjustmentRequest;
import com.operion.payroll.api.CreateSalaryComponentRequest;
import com.operion.payroll.api.CreateSalaryStructureRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/** Oversize payroll input must fail Bean Validation (400), not reach the database column limits. */
class PayrollRequestValidationTest {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	private static Set<String> invalidFields(Object request) {
		return VALIDATOR.validate(request).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
				.collect(Collectors.toSet());
	}

	@Test
	void componentNameIsBoundedToTheColumnLength() {
		assertThat(invalidFields(new CreateSalaryComponentRequest("x".repeat(100), ComponentType.EARNING,
				CalculationBasis.FLAT))).isEmpty();
		assertThat(invalidFields(new CreateSalaryComponentRequest("x".repeat(101), ComponentType.EARNING,
				CalculationBasis.FLAT))).containsExactly("name");
	}

	@Test
	void adjustmentLabelAndAmountAreBounded() {
		assertThat(invalidFields(new AddPayslipAdjustmentRequest("x".repeat(200), ComponentType.EARNING,
				new BigDecimal("999999999999.99")))).isEmpty();
		assertThat(invalidFields(new AddPayslipAdjustmentRequest("x".repeat(201), ComponentType.EARNING, BigDecimal.ONE)))
				.containsExactly("label");
		assertThat(invalidFields(new AddPayslipAdjustmentRequest("Late", ComponentType.EARNING,
				new BigDecimal("1000000000000.00")))).containsExactly("amount");
	}

	@Test
	void structureAmountsAreBounded() {
		LocalDate from = LocalDate.of(2026, 4, 1);
		CreateSalaryStructureRequest.Line ok = new CreateSalaryStructureRequest.Line(1L, new BigDecimal("9999999999.9999"));
		assertThat(invalidFields(new CreateSalaryStructureRequest(from, new BigDecimal("999999999999.99"), List.of(ok))))
				.isEmpty();

		assertThat(invalidFields(new CreateSalaryStructureRequest(from, new BigDecimal("1000000000000.00"), List.of())))
				.containsExactly("basicAmount");
		CreateSalaryStructureRequest.Line tooBig = new CreateSalaryStructureRequest.Line(1L, new BigDecimal("10000000000"));
		assertThat(invalidFields(new CreateSalaryStructureRequest(from, BigDecimal.TEN, List.of(tooBig))))
				.containsExactly("lines[0].value");
	}
}
