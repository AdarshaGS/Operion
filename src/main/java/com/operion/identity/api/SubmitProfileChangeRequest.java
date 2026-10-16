package com.operion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

public record SubmitProfileChangeRequest(
		@Pattern(regexp = "\\+?[0-9][0-9 ()-]{5,19}", message = "phone must be a phone number") String phone,
		@Email String email, String photoUrl) {
}
