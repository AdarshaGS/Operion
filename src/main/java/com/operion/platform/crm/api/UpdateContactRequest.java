package com.operion.platform.crm.api;

/** Every field optional - null leaves the current value unchanged, a blank string clears it. */
public record UpdateContactRequest(String name, String role, String email, String phone) {
}
