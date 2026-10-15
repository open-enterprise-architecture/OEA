package io.oea.config.security;

import io.oea.app.security.Sessions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates requests by the session cookie (design D1). */
public class OeaSessionFilter extends OncePerRequestFilter {

	private final Sessions sessions;

	public OeaSessionFilter(Sessions sessions) {
		this.sessions = sessions;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String token = SessionCookie.read(request);
		if (token != null) {
			this.sessions.authenticate(token).ifPresent((principal) -> SecurityContextHolder.getContext()
				.setAuthentication(new OeaAuthentication(principal, token)));
		}
		chain.doFilter(request, response);
	}

}
