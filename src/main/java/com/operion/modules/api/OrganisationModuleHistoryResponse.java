package com.operion.modules.api;

import java.time.Instant;

import com.operion.modules.OrganisationModuleSetting;

public record OrganisationModuleHistoryResponse(String code, String label, boolean enabled, String note, Long changedBy, Instant changedAt) {

	static OrganisationModuleHistoryResponse from(OrganisationModuleSetting setting) {
		return new OrganisationModuleHistoryResponse(setting.getModule().name(), setting.getModule().label(), setting.isEnabled(),
				setting.getNote(), setting.getCreatedBy(), setting.getCreatedAt());
	}
}
