package io.oea.config.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an implemented operation whose contract {@code conditions} cannot occur yet because the
 * handler rejects every request that would trigger them (e.g. connections in a batch before
 * US-141 AC2). Startup accepts such an operation only with a reason; the mark is removed when
 * the condition is enforced.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConditionsNotReachable {

	/** Why no request reaches a declared condition. */
	String value();

}
