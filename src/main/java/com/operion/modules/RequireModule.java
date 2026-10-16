package com.operion.modules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Pins a handler (or a whole controller) to a module regardless of its package. Needed
 * only where one controller serves several modules, e.g. the guardian portal's
 * attendance / fee / report-card endpoints. Method-level wins over class-level, which
 * wins over the package default in {@link AppModule#forPackage}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireModule {

	AppModule value();
}
