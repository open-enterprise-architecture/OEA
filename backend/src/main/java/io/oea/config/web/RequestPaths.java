package io.oea.config.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * The path of a request as the dispatcher resolves it: percent-decoded, without {@code ;}
 * segments and with {@code //} and dot segments normalized (security audit 2026-11 T-1, issue
 * #263; design D2 of s13-issue-fixes). Limits keyed on a path compare this path, never the raw
 * request URI, so that {@code /api/v1/entities/b%61tch} counts as the batch path.
 */
final class RequestPaths {

	private static final UrlPathHelper PATHS = new UrlPathHelper();

	static {
		PATHS.setUrlDecode(true);
		PATHS.setRemoveSemicolonContent(true);
	}

	private RequestPaths() {
	}

	static String normalized(HttpServletRequest request) {
		String path = PATHS.getPathWithinApplication(request);
		String normalized = org.springframework.util.StringUtils.cleanPath(path).replaceAll("/{2,}", "/");
		return (normalized.length() > 1 && normalized.endsWith("/")) ? normalized.substring(0, normalized.length() - 1)
				: normalized;
	}

}
