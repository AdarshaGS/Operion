package com.operion.organisation;

/** The org's first login, created as part of provisioning and granted the Org Admin role. */
public record NewAdminAccount(String email, String password, String firstName, String lastName) {
}
