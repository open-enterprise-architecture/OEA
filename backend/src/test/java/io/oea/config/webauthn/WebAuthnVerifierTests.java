package io.oea.config.webauthn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Relying party of passkeys (design D1 of us-022-passkeys). */
class WebAuthnVerifierTests {

	@Test
	void theRelyingPartyComesFromThePublicUrl() {
		WebAuthnVerifier.RelyingParty rp = WebAuthnVerifier
			.resolve(new PasskeyProperties("", "OEA", List.of()), "https://oea.example.org/portal/");
		assertThat(rp.id()).isEqualTo("oea.example.org");
		assertThat(rp.origins()).isEqualTo(Set.of("https://oea.example.org"));
	}

	@Test
	void settingsTakePrecedence() {
		WebAuthnVerifier.RelyingParty rp = WebAuthnVerifier.resolve(
				new PasskeyProperties("example.org", "ACME", List.of("https://ea.example.org", "https://oea.example.org:8443")),
				"https://oea.example.org");
		assertThat(rp.id()).isEqualTo("example.org");
		assertThat(rp.name()).isEqualTo("ACME");
		assertThat(rp.origins()).containsExactlyInAnyOrder("https://ea.example.org", "https://oea.example.org:8443");
	}

	@Test
	void withoutPublicUrlPasskeysAreUnavailable() {
		assertThat(WebAuthnVerifier.resolve(new PasskeyProperties("", "OEA", List.of()), "")).isNull();
		assertThat(new WebAuthnVerifier(new PasskeyProperties("", "OEA", List.of()), "").relyingParty()).isEmpty();
	}

	@Test
	void theRelyingPartyIdMustCoverEveryOrigin() {
		assertThatIllegalArgumentException().isThrownBy(() -> WebAuthnVerifier
			.resolve(new PasskeyProperties("other.example", "OEA", List.of()), "https://oea.example.org"));
		assertThatIllegalArgumentException().isThrownBy(() -> WebAuthnVerifier
			.resolve(new PasskeyProperties("example.org", "OEA", List.of("https://evil.example")), ""));
	}

}
