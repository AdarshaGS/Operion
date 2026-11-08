package com.operion.hr.api;

/** What the public application form gets back: deliberately the same for a new application and for a repeat from an
 * email that has already applied, and carrying no stored data (no id, status or email echo). */
public record PublicJobApplicationResponse(String status) {

	public static PublicJobApplicationResponse received() {
		return new PublicJobApplicationResponse("RECEIVED");
	}
}
