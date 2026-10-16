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
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Maximum size of uploaded packages (REQ-184 AC2, design D3 of us-033-import-metamodel): a
 * declared length above the limit is rejected with 413 before the body is read; a body without
 * declared length fails with {@link PackageTooLargeException} once it exceeds the limit.
 */
public class PackageSizeLimitFilter extends OncePerRequestFilter {

	private final long maximumBytes;

	public PackageSizeLimitFilter(long maximumBytes) {
		this.maximumBytes = maximumBytes;
	}

	/** Thrown while reading a body beyond the limit; answered with 413. */
	public static class PackageTooLargeException extends IOException {

		public PackageTooLargeException(long maximumBytes) {
			super("package larger than " + maximumBytes + " bytes");
		}

	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getContentLengthLong() > this.maximumBytes) {
			Problems.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "package-too-large",
					"Package too large");
			return;
		}
		chain.doFilter(new LimitedRequest(request, this.maximumBytes), response);
	}

	private static final class LimitedRequest extends HttpServletRequestWrapper {

		private final long maximumBytes;

		LimitedRequest(HttpServletRequest request, long maximumBytes) {
			super(request);
			this.maximumBytes = maximumBytes;
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
					if (this.read > LimitedRequest.this.maximumBytes) {
						throw new PackageTooLargeException(LimitedRequest.this.maximumBytes);
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
