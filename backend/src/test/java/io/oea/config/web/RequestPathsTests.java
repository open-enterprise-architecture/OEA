package io.oea.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

/** The decoded request path of the limits (security audit 2026-11 T-1, issue #263; design D2 of s13-issue-fixes). */
class RequestPathsTests {

	@ParameterizedTest
	@CsvSource({ "/api/v1/entities/batch, /api/v1/entities/batch", "/api/v1/entities/b%61tch, /api/v1/entities/batch",
			"/api/v1/entities/batc%68, /api/v1/entities/batch", "/api/v1/entities//batch, /api/v1/entities/batch",
			"/api/v1/./entities/batch, /api/v1/entities/batch", "/api/v1/entities/batch;x=1, /api/v1/entities/batch",
			"/api/v1/entities/batch/, /api/v1/entities/batch", "/api/v1/entities/b%61tches, /api/v1/entities/batches",
			"/api/v1/catalogs, /api/v1/catalogs" })
	void normalizedPath(String requestUri, String expected) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", requestUri);
		assertThat(RequestPaths.normalized(request)).isEqualTo(expected);
	}

}
