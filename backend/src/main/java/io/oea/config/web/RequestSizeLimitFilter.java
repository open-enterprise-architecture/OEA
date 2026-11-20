package io.oea.config.web;

import io.oea.config.security.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Size limit of request bodies (REQ-186, REQ-184 AC2; design D1 of s2b-backend-hardening): a
 * declared length above the limit is rejected with 413 before the body is read; a body without
 * declared length fails with {@link RequestTooLargeException} once it exceeds the limit, which
 * the exception handler answers with 413. The limit depends on the path, by exact path or by pattern; other
 * paths get the default.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

	/** A limit with the problem type and title of its rejection. */
	public record Limit(long maximumBytes, String problemType, String title) {
	}

	private final Limit defaultLimit;

	private final Map<String, Limit> limitsByPath;

	private final List<PatternLimit> limitsByPattern;

	/** A limit for the paths that match a pattern, tried after the exact paths. */
	public record PatternLimit(Pattern path, Limit limit) {
	}

	public RequestSizeLimitFilter(Limit defaultLimit, Map<String, Limit> limitsByPath) {
		this(defaultLimit, limitsByPath, List.of());
	}

	public RequestSizeLimitFilter(Limit defaultLimit, Map<String, Limit> limitsByPath,
			List<PatternLimit> limitsByPattern) {
		this.defaultLimit = defaultLimit;
		this.limitsByPath = Map.copyOf(limitsByPath);
		this.limitsByPattern = List.copyOf(limitsByPattern);
	}

	private Limit limit(String path) {
		Limit exact = this.limitsByPath.get(path);
		if (exact != null) {
			return exact;
		}
		return this.limitsByPattern.stream()
			.filter((candidate) -> candidate.path().matcher(path).matches())
			.map(PatternLimit::limit)
			.findFirst()
			.orElse(this.defaultLimit);
	}

	/** Thrown while reading a body beyond the limit; answered with 413. */
	public static class RequestTooLargeException extends IOException {

		private final transient Limit limit;

		RequestTooLargeException(Limit limit) {
			super("request body larger than " + limit.maximumBytes() + " bytes");
			this.limit = limit;
		}

		public String getProblemType() {
			return this.limit.problemType();
		}

		public String getTitle() {
			return this.limit.title();
		}

	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Limit limit = limit(RequestPaths.normalized(request));
		if (request.getContentLengthLong() > limit.maximumBytes()) {
			Problems.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, limit.problemType(),
					limit.title());
			return;
		}
		chain.doFilter(new LimitedRequest(request, limit), response);
	}

	private static final class LimitedRequest extends HttpServletRequestWrapper {

		private final Limit limit;

		LimitedRequest(HttpServletRequest request, Limit limit) {
			super(request);
			this.limit = limit;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			ServletInputStream delegate = super.getInputStream();
			return new ServletInputStream() {

				private long read;

				@Override
				public int read() throws IOException {
					int value = delegate.read();
					if (value >= 0) {
						count(1);
					}
					return value;
				}

				@Override
				public int read(byte[] buffer, int offset, int length) throws IOException {
					int count = delegate.read(buffer, offset, length);
					if (count > 0) {
						count(count);
					}
					return count;
				}

				private void count(int bytes) throws IOException {
					this.read += bytes;
					if (this.read > LimitedRequest.this.limit.maximumBytes()) {
						throw new RequestTooLargeException(LimitedRequest.this.limit);
					}
				}

				@Override
				public boolean isFinished() {
					return delegate.isFinished();
				}

				@Override
				public boolean isReady() {
					return delegate.isReady();
				}

				@Override
				public void setReadListener(ReadListener listener) {
					delegate.setReadListener(listener);
				}

			};
		}

	}

}
