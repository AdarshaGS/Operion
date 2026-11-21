package com.operion.hr.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Optional login access for a staff member: a User (invited by email) plus an OrganisationMembership with {@code roleId}.
 * {@code campusId} overrides the staff member's own campus for the membership (null = use theirs); {@code phone} is the
 * login's optional phone. The membership takes the staff member's department and joining date. Email and phone are capped at
 * the users.email (255) and users.phone (20) column lengths. */
public record StaffAccessRequest(@NotBlank @Size(max = 255) @Email(message = "must be a valid email address") String email,
		@NotNull Long roleId, Long campusId, @Size(max = 20) String phone) {
}
