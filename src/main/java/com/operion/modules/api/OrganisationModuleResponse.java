package com.operion.modules.api;

import com.operion.modules.ModuleAccessService.ModuleState;

public record OrganisationModuleResponse(String code, String label, boolean enabled) {

	static OrganisationModuleResponse from(ModuleState state) {
		return new OrganisationModuleResponse(state.module().name(), state.module().label(), state.enabled());
	}
}
