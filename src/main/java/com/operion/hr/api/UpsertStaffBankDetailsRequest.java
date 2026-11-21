package com.operion.hr.api;

import jakarta.validation.constraints.Size;

/** Lengths match the staff_bank_details columns. */
public record UpsertStaffBankDetailsRequest(
		@Size(max = 150) String bankAccountHolderName, @Size(max = 50) String bankAccountNumber, @Size(max = 150) String bankName,
		@Size(max = 30) String bankBranchCode, @Size(max = 100) String taxIdentifier) {
}
