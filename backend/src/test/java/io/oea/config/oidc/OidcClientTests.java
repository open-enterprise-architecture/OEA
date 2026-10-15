package io.oea.config.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.auth.DevRealm;
import io.oea.testsupport.auth.OidcLogin;
import io.oea.testsupport.containers.OeaKeycloak;
import io.oea.testsupport.time.MutableClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

/** Spec identity/oidc-login "Invalid identity token" (REQ-166 AC2) against the development realm. */
class OidcClientTests {

	private static final String CLIENT = "oea-portal";

	private final MutableClock clock = new MutableClock();

	private final OidcClient client = new OidcClient(this.clock);

	@Test
	void validTokenOfThePortalClientIsAccepted() {
		Jwt jwt = this.client.validateIdToken(settings(), OidcLogin.login(OeaKeycloak.issuer(), "dev-viewer").idToken(),
				null);
		assertThat(jwt.getSubject()).isEqualTo(DevRealmSubjects.DEV_VIEWER);
		assertThat(jwt.getIssuer()).hasToString(OeaKeycloak.issuer());
	}

	@Test
	void tokenForAnotherApplicationIsRejected() {
		String foreign = OidcLogin
			.login(OeaKeycloak.issuer(), "oea-test-foreign-app", "http://localhost:9999/callback", "dev-viewer")
			.idToken();
		assertThatExceptionOfType(InvalidIdTokenException.class)
			.isThrownBy(() -> this.client.validateIdToken(settings(), foreign, null))
			.withMessageContaining("invalid_id_token");
	}

	@Test
	void expiredTokenIsRejected() {
		String idToken = OidcLogin.login(OeaKeycloak.issuer(), "dev-viewer").idToken();
		this.clock.advance(Duration.ofHours(2));
		assertThatExceptionOfType(InvalidIdTokenException.class)
			.isThrownBy(() -> this.client.validateIdToken(settings(), idToken, null));
	}

	@Test
	void mismatchingNonceIsRejected() {
		String idToken = OidcLogin.login(OeaKeycloak.issuer(), "dev-viewer").idToken();
		assertThatExceptionOfType(InvalidIdTokenException.class)
			.isThrownBy(() -> this.client.validateIdToken(settings(), idToken, "another-nonce"))
			.withMessageContaining("invalid_nonce");
	}

	@Test
	void unsignedAndSymmetricallySignedTokensAreRejected() throws Exception {
		String claims = JsonMapper.shared()
			.createObjectNode()
			.put("iss", OeaKeycloak.issuer())
			.put("sub", DevRealmSubjects.DEV_VIEWER)
			.put("aud", CLIENT)
			.put("iat", this.clock.instant().getEpochSecond())
			.put("exp", this.clock.instant().getEpochSecond() + 300)
			.toString();
		String none = base64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + base64(claims) + ".";
		assertThatExceptionOfType(InvalidIdTokenException.class)
			.isThrownBy(() -> this.client.validateIdToken(settings(), none, null));

		String unsigned = base64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}") + "." + base64(claims);
		Mac mac = Mac.getInstance("HmacSHA256");
		// an attacker would use a public value such as the client ID as HMAC key
		mac.init(new SecretKeySpec(CLIENT.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String hs256 = unsigned + "." + Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.US_ASCII)));
		assertThatExceptionOfType(InvalidIdTokenException.class)
			.isThrownBy(() -> this.client.validateIdToken(settings(), hs256, null));
	}

	@Test
	void unreachableProviderIsReportedAsUnavailable() {
		OidcClientSettings unreachable = new OidcClientSettings(UUID.randomUUID(), "http://127.0.0.1:9/realms/none",
				CLIENT, null, Set.of("openid"));
		assertThatExceptionOfType(OidcProviderUnavailableException.class).isThrownBy(
				() -> this.client.authorizationUri(unreachable, "http://localhost/cb", "s", "n", "c"));
	}

	private static OidcClientSettings settings() {
		return new OidcClientSettings(UUID.randomUUID(), OeaKeycloak.issuer(), CLIENT, DevRealm.clientSecret(CLIENT),
				Set.of("openid", "profile"));
	}

	private static String base64(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	/** Subjects of the realm file (fixed user IDs). */
	static final class DevRealmSubjects {

		static final String DEV_VIEWER = "a8002a18-4096-52a9-a792-0b0ff0981a5a";

	}

}
