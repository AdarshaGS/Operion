package com.operion.hr.api;

import com.operion.hr.StaffBankDetail;

public record StaffBankDetailResponse(
		Long id, Long staffProfileId, String bankAccountHolderName, String bankAccountNumber, String bankName,
		String bankBranchCode, String taxIdentifier) {

	public static StaffBankDetailResponse from(StaffBankDetail detail) {
		return new StaffBankDetailResponse(detail.getId(), detail.getStaffProfile().getId(), detail.getBankAccountHolderName(),
				mask(detail.getBankAccountNumber()), detail.getBankName(), detail.getBankBranchCode(), detail.getTaxIdentifier());
	}

	/** Only the last four digits ever leave the server; the full number stays in the database for payroll. */
	static String mask(String accountNumber) {
		if (accountNumber == null || accountNumber.length() <= 4) {
			return accountNumber;
		}
		return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
	}
}
