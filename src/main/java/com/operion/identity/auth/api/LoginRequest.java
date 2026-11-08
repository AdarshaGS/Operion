package com.operion.identity.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** password is deliberately NOT @NotNull: a missing password must be rejected exactly like a wrong
 * one (401, same body, same bcrypt work) - answering 400/404 for it would be an enumeration oracle.
 * The size cap only bounds the request; bcrypt itself refuses anything over 72 bytes. */
public record LoginRequest(@NotBlank @Size(max = 100) String organisationSlug, @NotBlank @Size(max = 254) String email,
		@Size(max = 256) String password) {
}
