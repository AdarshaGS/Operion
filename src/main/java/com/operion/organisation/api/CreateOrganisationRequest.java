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

public record CreateOrganisationRequest(@NotBlank @Size(max = 200) String name, @Size(max = 200) String legalName,
		@NotBlank @Size(max = 63, message = "must be at most 63 characters")
		@Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "slug must be lower-case letters, digits and single hyphens") String slug,
		@NotBlank @Email @Size(max = 200) String adminEmail,
		@NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters") String adminPassword,
		@Size(max = 100) String adminFirstName, @Size(max = 100) String adminLastName,
		OrganisationType organisationType, SchoolBoard board, @Size(max = 50) String schoolCode,
		@Size(max = 200) String primaryContactName, @Email @Size(max = 200) String primaryContactEmail, @Size(max = 30) String primaryContactPhone,
		@Size(max = 200) String addressLine1, @Size(max = 200) String addressLine2, @Size(max = 100) String city, @Size(max = 100) String state,
		@Size(max = 100) String country, @Size(max = 20) String pincode,
		@Size(max = 64) String timezone, @Size(max = 100) String academicYearName, LocalDate academicYearStartDate, LocalDate academicYearEndDate,
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
