package io.oea.config.security;

import io.oea.app.security.Sessions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates requests by the session cookie (design D1). */
public class OeaSessionFilter extends OncePerRequestFilter {

	/**
	 * Security schemes of the contract that this release accepts; all others must be marked as
	 * planned ({@link SecuritySchemeCoverageCheck}).
	 */
	public static final Set<String> ACCEPTED_SCHEMES = Set.of("cookieAuth", "bearerAuth", "oauth2ClientCredentials",
			"clientSecretBasic");

	private final Sessions sessions;

	public OeaSessionFilter(Sessions sessions) {
		this.sessions = sessions;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String token = SessionCookie.read(request);
		// a bearer authentication of OeaTokenFilter is kept
		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			this.sessions.authenticate(token).ifPresent((principal) -> SecurityContextHolder.getContext()
				.setAuthentication(new OeaAuthentication(principal, token)));
		}
		chain.doFilter(request, response);
	}

}
