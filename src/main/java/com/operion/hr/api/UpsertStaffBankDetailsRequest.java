package com.operion.hr.api;

public record UpsertStaffBankDetailsRequest(
		String bankAccountHolderName, String bankAccountNumber, String bankName, String bankBranchCode, String taxIdentifier) {
}
