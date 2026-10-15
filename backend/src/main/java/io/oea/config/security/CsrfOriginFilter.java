package io.oea.config.security;

import io.oea.app.security.Sessions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF protection for state-changing requests (REQ-187 AC3, design D2): an {@code Origin}
 * header, if sent, must be the own origin or an allowed one; requests authenticated by the
 * session cookie additionally need {@code X-XSRF-TOKEN} matching the token derived from it.
 */
public class CsrfOriginFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-XSRF-TOKEN";

	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

	private final List<String> allowedOrigins;

	public CsrfOriginFilter(List<String> allowedOrigins) {
		this.allowedOrigins = allowedOrigins.stream().map(CsrfOriginFilter::normalize).toList();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!SAFE_METHODS.contains(request.getMethod())) {
			String origin = request.getHeader("Origin");
			if (origin != null && !originAllowed(request, origin)) {
				reject(response);
				return;
			}
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication instanceof OeaAuthentication session && !tokenMatches(request, session)) {
				reject(response);
				return;
			}
		}
		chain.doFilter(request, response);
	}

	private boolean originAllowed(HttpServletRequest request, String origin) {
		String normalized = normalize(origin);
		String own = normalize(request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort());
		return normalized.equals(own) || this.allowedOrigins.contains(normalized);
	}

	private static boolean tokenMatches(HttpServletRequest request, OeaAuthentication session) {
		String presented = request.getHeader(HEADER);
		return presented != null && MessageDigest.isEqual(presented.getBytes(StandardCharsets.US_ASCII),
				Sessions.csrfToken(session.sessionToken()).getBytes(StandardCharsets.US_ASCII));
	}

	private static void reject(HttpServletResponse response) throws IOException {
		Problems.write(response, HttpServletResponse.SC_FORBIDDEN, "csrf-rejected", "Request rejected");
	}

	/** {@code scheme://host:port} with explicit default ports, lower case. */
	static String normalize(String origin) {
		try {
			URI uri = URI.create(origin.strip());
			int port = uri.getPort();
			if (port == -1) {
				port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
			}
			return (uri.getScheme() + "://" + uri.getHost() + ":" + port).toLowerCase(Locale.ROOT);
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return "invalid";
		}
	}

}
