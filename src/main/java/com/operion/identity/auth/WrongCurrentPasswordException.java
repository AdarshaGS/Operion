package com.operion.identity.auth;

/**
 * The current password given to change-password did not match. Still an IllegalStateException so it
 * keeps mapping to 409 (see AuthenticationService.changePassword for why that is not a 401); it is its
 * own type only so the controller can count these - and only these - against the per-IP guess limit.
 */
public class WrongCurrentPasswordException extends IllegalStateException {

	public WrongCurrentPasswordException() {
		super("Current password is incorrect");
	}
}
