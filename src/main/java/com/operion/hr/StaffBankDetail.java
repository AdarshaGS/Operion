package com.operion.hr;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1:1 with StaffProfile, split into its own table (rather than columns on StaffProfile)
 * so access can be gated by HR_PAYROLL_VIEW separately from ordinary HR_VIEW/
 * HR_STAFF_MANAGE - viewing someone's designation should never imply seeing their bank
 * details. Upserted as a whole, same "one row, update in place" convention as
 * OrganisationConfiguration.
 */
@Getter
@Entity
@Table(name = "staff_bank_details")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffBankDetail extends TenantScopedEntity {

	@OneToOne(optional = false)
	@JoinColumn(name = "staff_profile_id")
	private StaffProfile staffProfile;

	@Column(name = "bank_account_holder_name")
	private String bankAccountHolderName;

	@Column(name = "bank_account_number")
	private String bankAccountNumber;

	@Column(name = "bank_name")
	private String bankName;

	@Column(name = "bank_branch_code")
	private String bankBranchCode;

	@Column(name = "tax_identifier")
	private String taxIdentifier;

	public StaffBankDetail(StaffProfile staffProfile, String bankAccountHolderName, String bankAccountNumber,
			String bankName, String bankBranchCode, String taxIdentifier) {
		this.staffProfile = staffProfile;
		update(bankAccountHolderName, bankAccountNumber, bankName, bankBranchCode, taxIdentifier);
	}

	public void update(String bankAccountHolderName, String bankAccountNumber, String bankName, String bankBranchCode,
			String taxIdentifier) {
		this.bankAccountHolderName = bankAccountHolderName;
		this.bankAccountNumber = bankAccountNumber;
		this.bankName = bankName;
		this.bankBranchCode = bankBranchCode;
		this.taxIdentifier = taxIdentifier;
	}
}
