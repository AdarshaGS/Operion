package com.operion.common.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link Transactional} bound to the Platform database. A plain {@code @Transactional}
 * binds to the School database (the primary transaction manager), so any method that
 * writes a {@link PlatformPlane} entity - or needs managed platform entities across several
 * repository calls - must use this instead. There is no transaction spanning both
 * databases: a method that writes to both is ordered so a failure on the second leaves the
 * first compensable (see OrganisationService.provision).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@Transactional(transactionManager = PersistenceConfig.PLATFORM_TRANSACTION_MANAGER)
public @interface PlatformTransactional {

	@AliasFor(annotation = Transactional.class, attribute = "readOnly")
	boolean readOnly() default false;
}
