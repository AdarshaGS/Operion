package com.operion.organisation.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.operion.organisation.AcademicYearDetails;
import com.operion.organisation.NewAdminAccount;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationType;
import com.operion.organisation.PlanSelection;
import com.operion.organisation.ProvisioningProfile;
import com.operion.organisation.SchoolBoard;

public record CreateOrganisationRequest(@NotBlank String name, String legalName,
		@NotBlank @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "slug must be lower-case letters, digits and single hyphens") String slug,
		@NotBlank @Email String adminEmail, @NotBlank @Size(min = 8, message = "must be at least 8 characters") String adminPassword,
		String adminFirstName, String adminLastName,
		OrganisationType organisationType, SchoolBoard board, String schoolCode,
		String primaryContactName, String primaryContactEmail, String primaryContactPhone,
		String addressLine1, String addressLine2, String city, String state, String country, String pincode,
		String timezone, String academicYearName, LocalDate academicYearStartDate, LocalDate academicYearEndDate,
		Long planId, LocalDate planStartDate) {

	public Organisation toOrganisation() {
		Organisation organisation = new Organisation(name, legalName, slug);
		if (organisationType != null) {
			organisation.setOrganisationType(organisationType);
		}
		organisation.setBoard(board);
		organisation.setSchoolCode(schoolCode);
		return organisation;
	}

	public ProvisioningProfile toProfile() {
		return new ProvisioningProfile(timezone, primaryContactName, primaryContactEmail, primaryContactPhone,
				addressLine1, addressLine2, city, state, country, pincode);
	}

	public NewAdminAccount toAdminAccount() {
		return new NewAdminAccount(adminEmail, adminPassword, adminFirstName, adminLastName);
	}

	public AcademicYearDetails toAcademicYearDetails() {
		return academicYearName != null ? new AcademicYearDetails(academicYearName, academicYearStartDate, academicYearEndDate) : null;
	}

	public PlanSelection toPlanSelection() {
		return planId != null ? new PlanSelection(planId, planStartDate != null ? planStartDate : LocalDate.now()) : null;
	}
}
