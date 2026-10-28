package io.oea.config.web;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.config.security.OeaAuthentication;
import io.oea.config.security.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-principal rate limits (REQ-186 AC1; design D3 of s3-throttling-and-denials): every
 * request of an authenticated principal counts against the request limit, an entity batch
 * additionally against the batch limit. A request above a limit is answered with 429 and
 * {@code Retry-After} before its body is read; the first rejection of a window is audited as
 * {@code auth.throttled}. Unauthenticated requests are left to the security chain and the
 * login and setup throttling.
 */
public class RateLimitFilter extends OncePerRequestFilter {

	/** Limit name of all requests. */
	static final String REQUESTS = "requests";

	/** Limit name of entity batches. */
	static final String ENTITY_BATCH = "entity-batch";

	private final RateLimiter limiter;

	private final AuditWriter audit;

	private final int requestsPerMinute;

	private final int batchesPerMinute;

	public RateLimitFilter(RateLimiter limiter, AuditWriter audit, int requestsPerMinute, int batchesPerMinute) {
		this.limiter = limiter;
		this.audit = audit;
		this.requestsPerMinute = requestsPerMinute;
		this.batchesPerMinute = batchesPerMinute;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!(SecurityContextHolder.getContext().getAuthentication() instanceof OeaAuthentication authentication)) {
			chain.doFilter(request, response);
			return;
		}
		OeaPrincipal principal = authentication.getPrincipal();
		String key = (principal.type() == OeaPrincipal.Type.PERSON) ? "person:" + principal.personId()
				: "system-admin-account:" + principal.systemAdminAccountId();
		RateLimiter.Decision decision = this.limiter.acquire(key, REQUESTS, this.requestsPerMinute);
		int limit = this.requestsPerMinute;
		String limitName = REQUESTS;
		if (decision.allowed() && "POST".equals(request.getMethod())
				&& RequestSizeConfiguration.BATCH_PATH.equals(request.getRequestURI())) {
			decision = this.limiter.acquire(key, ENTITY_BATCH, this.batchesPerMinute);
			limit = this.batchesPerMinute;
			limitName = ENTITY_BATCH;
		}
		if (decision.allowed()) {
			chain.doFilter(request, response);
			return;
		}
		if (decision.firstRejection()) {
			this.audit.appendIndependently(new AuditEvent("auth.throttled", AuditEvent.Category.SECURITY,
					AuditEvent.Severity.WARNING, AuditEvent.Outcome.DENIED, AuditEvent.Source.API,
					Sessions.actor(principal), null, null,
					Map.of("scope", "principal", "limit", limitName, "limitPerMinute", limit)));
		}
		response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
		Problems.write(response, 429, "too-many-requests", "Too many requests");
	}

}
