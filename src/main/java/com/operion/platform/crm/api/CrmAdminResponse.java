package com.operion.platform.crm.api;

import com.operion.platform.PlatformAdmin;

public record CrmAdminResponse(Long id, String name) {

	public static CrmAdminResponse from(PlatformAdmin admin) {
		return new CrmAdminResponse(admin.getId(), admin.getName());
	}
}
