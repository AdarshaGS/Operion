package com.operion.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import com.operion.hr.api.AddStaffDocumentRequest;
import com.operion.hr.api.CreateLeaveTypeRequest;
import com.operion.hr.api.CreateStaffProfileRequest;
import com.operion.hr.api.RaiseLeaveRequestRequest;
import com.operion.hr.api.RecordStaffExitRequest;
import com.operion.hr.api.StaffAccessRequest;
import com.operion.hr.api.UpdateLeaveTypeRequest;
import com.operion.hr.api.UpsertStaffBankDetailsRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/** Oversize HR input must fail Bean Validation (400), not reach the database column limits (500). Each limit here is the
 * column length in school/V1__school_baseline.sql. */
class HrRequestValidationTest {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	private static Set<String> invalidFields(Object request) {
		return VALIDATOR.validate(request).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
				.collect(Collectors.toSet());
	}

	private static String text(int length) {
		return "x".repeat(length);
	}

	private static CreateStaffProfileRequest createStaff(String employeeCode, StaffAccessRequest access) {
		return new CreateStaffProfileRequest(1L, 1L, employeeCode, 1L, 1L, LocalDate.of(2024, 6, 1), "PERMANENT", null, access);
	}

	@Test
	void employeeCodeIsBoundedTo30() {
		assertThat(invalidFields(createStaff(text(30), null))).isEmpty();
		assertThat(invalidFields(createStaff(text(31), null))).containsExactly("employeeCode");
	}

	@Test
	void theAccessBlockIsValidatedWhenNestedInTheCreateRequest() {
		StaffAccessRequest ok = new StaffAccessRequest("a@b.test", 1L, null, text(20));
		assertThat(invalidFields(createStaff("EMP-1", ok))).isEmpty();

		StaffAccessRequest longPhone = new StaffAccessRequest("a@b.test", 1L, null, text(21));
		assertThat(invalidFields(createStaff("EMP-1", longPhone))).containsExactly("access.phone");
	}

	@Test
	void accessEmailIsBoundedTo255AndMustBeAnEmail() {
		// A syntactically valid address (labels of at most 63 characters) that is exactly 255 long.
		String atLimit = "a@" + text(63) + "." + text(63) + "." + text(63) + "." + text(61);
		assertThat(atLimit).hasSize(255);
		assertThat(invalidFields(new StaffAccessRequest(atLimit, 1L, null, null))).isEmpty();
		assertThat(invalidFields(new StaffAccessRequest(atLimit + "x", 1L, null, null))).contains("email");
		assertThat(invalidFields(new StaffAccessRequest("not-an-email", 1L, null, null))).containsExactly("email");
		assertThat(invalidFields(new StaffAccessRequest(" ", 1L, null, null))).contains("email");
		assertThat(invalidFields(new StaffAccessRequest("a@b.test", null, null, null))).containsExactly("roleId");
	}

	@Test
	void accessPhoneIsBoundedTo20() {
		assertThat(invalidFields(new StaffAccessRequest("a@b.test", 1L, null, text(20)))).isEmpty();
		assertThat(invalidFields(new StaffAccessRequest("a@b.test", 1L, null, text(21)))).containsExactly("phone");
	}

	@Test
	void leaveTypeCodeAndNameAreBounded() {
		assertThat(invalidFields(new CreateLeaveTypeRequest(text(30), text(100), 12.0))).isEmpty();
		assertThat(invalidFields(new CreateLeaveTypeRequest(text(31), text(100), 12.0))).containsExactly("code");
		assertThat(invalidFields(new CreateLeaveTypeRequest(text(30), text(101), 12.0))).containsExactly("name");
		assertThat(invalidFields(new UpdateLeaveTypeRequest(text(100), 12.0))).isEmpty();
		assertThat(invalidFields(new UpdateLeaveTypeRequest(text(101), 12.0))).containsExactly("name");
	}

	@Test
	void exitAndLeaveReasonsAreBoundedTo500() {
		assertThat(invalidFields(new RecordStaffExitRequest("RESIGNATION", LocalDate.of(2025, 1, 1), text(500), null))).isEmpty();
		assertThat(invalidFields(new RecordStaffExitRequest("RESIGNATION", LocalDate.of(2025, 1, 1), text(501), null)))
				.containsExactly("reason");
		LocalDate day = LocalDate.of(2025, 1, 1);
		assertThat(invalidFields(new RaiseLeaveRequestRequest(1L, 1L, 1L, day, day, 1, text(500)))).isEmpty();
		assertThat(invalidFields(new RaiseLeaveRequestRequest(1L, 1L, 1L, day, day, 1, text(501)))).containsExactly("reason");
	}

	@Test
	void staffDocumentFieldsAreBounded() {
		assertThat(invalidFields(new AddStaffDocumentRequest(text(100), text(500), text(255), text(100), null))).isEmpty();
		assertThat(invalidFields(new AddStaffDocumentRequest(text(101), text(501), text(256), text(101), null)))
				.containsExactlyInAnyOrder("documentType", "fileReference", "fileName", "mimeType");
	}

	@Test
	void bankDetailFieldsAreBounded() {
		assertThat(invalidFields(new UpsertStaffBankDetailsRequest(text(150), text(50), text(150), text(30), text(100)))).isEmpty();
		assertThat(invalidFields(new UpsertStaffBankDetailsRequest(text(151), text(51), text(151), text(31), text(101))))
				.containsExactlyInAnyOrder("bankAccountHolderName", "bankAccountNumber", "bankName", "bankBranchCode", "taxIdentifier");
	}
}
