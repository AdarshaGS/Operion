package com.operion.hr.api;

import com.operion.hr.StaffBankDetail;

public record StaffBankDetailResponse(
		Long id, Long staffProfileId, String bankAccountHolderName, String bankAccountNumber, String bankName,
		String bankBranchCode, String taxIdentifier) {

	public static StaffBankDetailResponse from(StaffBankDetail detail) {
		return new StaffBankDetailResponse(detail.getId(), detail.getStaffProfile().getId(), detail.getBankAccountHolderName(),
				detail.getBankAccountNumber(), detail.getBankName(), detail.getBankBranchCode(), detail.getTaxIdentifier());
	}
}
