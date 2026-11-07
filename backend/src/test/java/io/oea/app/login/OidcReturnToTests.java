package io.oea.app.login;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The return path after an OIDC login: only relative portal paths of at most 512 characters are
 * kept, anything else opens the start page (no open redirect).
 */
class OidcReturnToTests {

	@Test
	void relativePathIsKept() {
		assertThat(OidcLoginFlow.safeReturnTo("/catalogs/42?page=2")).isEqualTo("/catalogs/42?page=2");
	}

	@Test
	void otherTargetsOpenTheStartPage() {
		assertThat(OidcLoginFlow.safeReturnTo(null)).isEqualTo("/");
		assertThat(OidcLoginFlow.safeReturnTo("https://evil.example/")).isEqualTo("/");
		assertThat(OidcLoginFlow.safeReturnTo("//evil.example/")).isEqualTo("/");
		assertThat(OidcLoginFlow.safeReturnTo("/\\evil.example")).isEqualTo("/");
	}

	@Test
	void lengthAtItsLimit() {
		String longest = "/" + "a".repeat(511);
		assertThat(OidcLoginFlow.safeReturnTo(longest)).isEqualTo(longest);
		assertThat(OidcLoginFlow.safeReturnTo(longest + "a")).isEqualTo("/");
	}

}
