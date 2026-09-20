package com.operion.hr.api;

import java.time.LocalDate;

import com.operion.hr.StaffProfile;
import com.operion.identity.Person;

public record StaffExportResponse(Long id, String employeeCode, String firstName, String lastName, String email, String phone,
		String department, String designation, String employmentType, LocalDate dateOfJoining, String status) {

	static StaffExportResponse from(StaffProfile staffProfile) {
		Person person = staffProfile.getPerson();
		return new StaffExportResponse(staffProfile.getId(), staffProfile.getEmployeeCode(), person.getFirstName(), person.getLastName(),
				person.getEmail(), person.getPhone(),
				staffProfile.getDepartment() == null ? null : staffProfile.getDepartment().getName(),
				staffProfile.getDesignation().getName(), staffProfile.getEmploymentType().name(),
				staffProfile.getDateOfJoining(), staffProfile.getStatus().name());
	}
}
