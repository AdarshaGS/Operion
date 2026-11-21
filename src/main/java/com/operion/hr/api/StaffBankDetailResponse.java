package com.operion.hr.api;

import com.operion.hr.StaffBankDetail;

public record StaffBankDetailResponse(
		Long id, Long staffProfileId, String bankAccountHolderName, String bankAccountNumber, String bankName,
		String bankBranchCode, String taxIdentifier) {

	/** {@code full} is true for callers who hold HR_STAFF_MANAGE (they edit the number, so they get it whole); everyone else
	 * receives the account number and the tax identifier (PAN-like) masked down to their last four characters. */
	public static StaffBankDetailResponse from(StaffBankDetail detail, boolean full) {
		return new StaffBankDetailResponse(detail.getId(), detail.getStaffProfile().getId(), detail.getBankAccountHolderName(),
				full ? detail.getBankAccountNumber() : mask(detail.getBankAccountNumber()), detail.getBankName(), detail.getBankBranchCode(),
				full ? detail.getTaxIdentifier() : mask(detail.getTaxIdentifier()));
	}

	/** Only the last four characters ever leave the server for a non-manager; a value of four or fewer characters is hidden
	 * entirely, since showing it would be showing all of it. The full value stays in the database for payroll. */
	static String mask(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		if (value.length() <= 4) {
			return "*".repeat(value.length());
		}
		return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
	}
}
