package io.oea.config.security;

import io.oea.app.security.ActingPrincipal;
import io.oea.app.security.OeaPrincipal;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** The acting principal from the Spring Security context of the current thread. */
@Component
class SecurityContextActingPrincipal implements ActingPrincipal {

	@Override
	public Optional<OeaPrincipal> current() {
		return (SecurityContextHolder.getContext().getAuthentication() instanceof OeaAuthentication authentication)
				? Optional.ofNullable(authentication.getPrincipal()) : Optional.empty();
	}

}
