package com.operion.modules;

import com.operion.authorization.AuthorizationDeniedException;

/** Extends AuthorizationDeniedException so ApiExceptionHandler already answers it with a 403. */
public class ModuleDisabledException extends AuthorizationDeniedException {

	public ModuleDisabledException(AppModule module) {
		super("The " + module.label() + " module is not enabled for this organisation");
	}
}
