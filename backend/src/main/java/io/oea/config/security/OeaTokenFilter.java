package io.oea.config.security;

import io.oea.app.login.AccessTokens;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests by an access token in the {@code Authorization: Bearer} header (ADR-050
 * rule 10; design D5 of s12-client-credentials-token). The token is accepted only in that header;
 * a request carrying both a bearer token and a session cookie, and a token that is unknown,
 * expired, revoked or of a deactivated service account, are answered with 401 at once.
 */
public class OeaTokenFilter extends OncePerRequestFilter {

	private static final String BEARER = "bearer ";

	private final AccessTokens tokens;

	public OeaTokenFilter(AccessTokens tokens) {
		this.tokens = tokens;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.toLowerCase(Locale.ROOT).startsWith(BEARER)) {
			chain.doFilter(request, response);
			return;
		}
		if (SessionCookie.read(request) != null) {
			reject(response);
			return;
		}
		String token = authorization.substring(BEARER.length()).strip();
		var principal = this.tokens.authenticate(token);
		if (principal.isEmpty()) {
			reject(response);
			return;
		}
		SecurityContextHolder.getContext().setAuthentication(OeaAuthentication.bearer(principal.get()));
		chain.doFilter(request, response);
	}

	private static void reject(HttpServletResponse response) throws IOException {
		response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
		Problems.write(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthenticated", "Authentication required");
	}

}
