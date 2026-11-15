package io.oea.api.identity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an operation that answers a malformed request with an RFC 6749 §5.2 error
 * ({@code invalid_request}) instead of a problem document: the token endpoint (design D3 of
 * s12-client-credentials-token). The exception handler reads it from the handler method, never
 * from the request path.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OAuthErrorResponse {

}
