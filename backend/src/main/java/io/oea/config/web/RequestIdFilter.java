package io.oea.config.web;

import io.oea.audit.AuditWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlation ID per request (ADR-033 §8): a well-formed {@code X-Request-Id} header is
 * kept, otherwise a UUID is generated. The ID is put into the log MDC, read by the audit
 * writer and returned in the response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";

	private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String supplied = request.getHeader(HEADER);
		String requestId = (supplied != null && VALID.matcher(supplied).matches()) ? supplied
				: UUID.randomUUID().toString();
		MDC.put(AuditWriter.REQUEST_ID, requestId);
		response.setHeader(HEADER, requestId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(AuditWriter.REQUEST_ID);
		}
	}

}
