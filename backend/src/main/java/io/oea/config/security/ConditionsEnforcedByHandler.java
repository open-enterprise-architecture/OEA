package io.oea.config.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an implemented operation whose contract {@code conditions} are enforced by the handler
 * itself, because they depend on the request body (e.g. {@code scope=instance} of a catalog
 * requires {@code catalog:manage}, design D3 of us-046-create-catalog). Startup accepts such an
 * operation only with a reason; the authorization matrix calls it with {@link #probeBody()} as a
 * holder of {@link #probeRole()} and expects 403.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConditionsEnforcedByHandler {

	/** Where and how the handler enforces the declared conditions. */
	String value();

	/** A built-in role holding the base permissions of the operation but not the conditional ones. */
	String probeRole();

	/** A valid request body that triggers the condition. */
	String probeBody();

}
