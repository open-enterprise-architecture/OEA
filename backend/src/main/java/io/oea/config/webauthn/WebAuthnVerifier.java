package io.oea.config.webauthn;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.exception.DataConversionException;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticatorTransport;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.verifier.exception.VerificationException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Verification of passkey registrations and assertions with WebAuthn4J (ADR-048; design D1, D3,
 * D4 of us-022-passkeys). User verification and user presence are always required.
 */
@Component
@EnableConfigurationProperties(PasskeyProperties.class)
public class WebAuthnVerifier {

	private final ObjectConverter converter = new ObjectConverter();

	private final WebAuthnManager manager = WebAuthnManager.createNonStrictWebAuthnManager(this.converter);

	private final AttestedCredentialDataConverter attested = new AttestedCredentialDataConverter(this.converter);

	private final RelyingParty relyingParty;

	/** The relying party the passkeys are bound to. */
	public record RelyingParty(String id, String name, Set<String> origins) {
	}

	/** A verified registration. */
	public record Registered(byte[] credentialId, byte[] attestedCredentialData, long signCount, boolean backupEligible,
			boolean backedUp, List<String> transports) {
	}

	/** The parts of an assertion that identify the passkey, before the verification. */
	public record Presented(byte[] credentialId, byte[] userHandle) {
	}

	/** A stored passkey, as the verification of an assertion needs it. */
	public record StoredPasskey(byte[] attestedCredentialData, long signCount, boolean backupEligible,
			boolean backedUp) {
	}

	/** A verified assertion. */
	public record Asserted(long signCount, boolean backedUp) {
	}

	public WebAuthnVerifier(PasskeyProperties properties, @Value("${oea.web.public-url:}") String publicUrl) {
		this.relyingParty = resolve(properties, publicUrl);
	}

	/** The relying party, empty without public URL and passkey settings (passkeys unavailable). */
	public Optional<RelyingParty> relyingParty() {
		return Optional.ofNullable(this.relyingParty);
	}

	/**
	 * Verifies a registration response (WebAuthn Level 3 JSON).
	 * @throws PasskeyRejectedException for any failed check
	 */
	public Registered verifyRegistration(String responseJson, byte[] challenge) {
		RelyingParty rp = require();
		try {
			RegistrationData data = this.manager.verifyRegistrationResponseJSON(responseJson,
					new RegistrationParameters(serverProperty(rp, challenge), null, true, true));
			AuthenticatorData<?> authenticatorData = data.getAttestationObject().getAuthenticatorData();
			if (!authenticatorData.isFlagUV() || !authenticatorData.isFlagUP()) {
				throw new PasskeyRejectedException("user not verified");
			}
			AttestedCredentialData credential = authenticatorData.getAttestedCredentialData();
			List<String> transports = (data.getTransports() != null)
					? data.getTransports().stream().map(AuthenticatorTransport::getValue).toList() : List.of();
			return new Registered(credential.getCredentialId(), this.attested.convert(credential),
					authenticatorData.getSignCount(), authenticatorData.isFlagBE(), authenticatorData.isFlagBS(),
					transports);
		}
		catch (VerificationException | DataConversionException | IllegalArgumentException ex) {
			throw new PasskeyRejectedException("registration rejected", ex);
		}
	}

	/**
	 * Reads credential ID and user handle of an assertion without verifying it.
	 * @throws PasskeyRejectedException for a malformed response
	 */
	public Presented presented(String responseJson) {
		try {
			AuthenticationData data = this.manager.parseAuthenticationResponseJSON(responseJson);
			return new Presented(data.getCredentialId(), data.getUserHandle());
		}
		catch (DataConversionException | IllegalArgumentException ex) {
			throw new PasskeyRejectedException("malformed assertion", ex);
		}
	}

	/**
	 * Verifies an assertion against a stored passkey. A signature counter that does not increase
	 * is rejected unless both counters are 0 (synced passkeys).
	 * @throws PasskeyRejectedException for any failed check
	 */
	public Asserted verifyAuthentication(String responseJson, byte[] challenge, StoredPasskey stored) {
		RelyingParty rp = require();
		try {
			AttestedCredentialData credential = this.attested.convert(stored.attestedCredentialData());
			CredentialRecordImpl record = new CredentialRecordImpl(new NoneAttestationStatement(), true,
					stored.backupEligible(), stored.backedUp(), stored.signCount(), credential, null, null, null, null);
			AuthenticationData data = this.manager.verifyAuthenticationResponseJSON(responseJson,
					new AuthenticationParameters(serverProperty(rp, challenge), record, null, true, true));
			AuthenticatorData<?> authenticatorData = data.getAuthenticatorData();
			if (!authenticatorData.isFlagUV() || !authenticatorData.isFlagUP()) {
				throw new PasskeyRejectedException("user not verified");
			}
			long count = authenticatorData.getSignCount();
			if ((count != 0 || stored.signCount() != 0) && count <= stored.signCount()) {
				throw new PasskeyRejectedException("signature counter did not increase");
			}
			return new Asserted(count, authenticatorData.isFlagBS());
		}
		catch (VerificationException | DataConversionException | IllegalArgumentException ex) {
			throw new PasskeyRejectedException("assertion rejected", ex);
		}
	}

	private RelyingParty require() {
		if (this.relyingParty == null) {
			throw new IllegalStateException("passkeys are unavailable without a relying party");
		}
		return this.relyingParty;
	}

	private static ServerProperty serverProperty(RelyingParty rp, byte[] challenge) {
		Set<Origin> origins = rp.origins().stream().map(Origin::new).collect(Collectors.toSet());
		return new ServerProperty(origins, rp.id(), new DefaultChallenge(challenge));
	}

	/** D1: settings first, the public URL second; the RP ID must cover the host of every origin. */
	static RelyingParty resolve(PasskeyProperties properties, String publicUrl) {
		String base = (publicUrl == null) ? "" : publicUrl.strip();
		Set<String> origins = new LinkedHashSet<>();
		for (String origin : properties.origins()) {
			origins.add(origin(origin));
		}
		if (origins.isEmpty() && !base.isEmpty()) {
			origins.add(origin(base));
		}
		String rpId = properties.rpId().isBlank() ? (base.isEmpty() ? "" : URI.create(base).getHost())
				: properties.rpId().strip();
		if (rpId == null || rpId.isEmpty() || origins.isEmpty()) {
			return null;
		}
		for (String origin : origins) {
			String host = URI.create(origin).getHost();
			if (host == null || !(host.equals(rpId) || host.endsWith("." + rpId))) {
				throw new IllegalArgumentException("oea.auth.passkey.rp-id " + rpId
						+ " must be the host or a parent domain of every origin, not of " + origin);
			}
		}
		return new RelyingParty(rpId, properties.rpName(), Set.copyOf(origins));
	}

	/** Scheme, host and port of a URL, without path. */
	private static String origin(String url) {
		URI uri = URI.create(url.strip());
		if (uri.getScheme() == null || uri.getHost() == null) {
			throw new IllegalArgumentException("not an origin: " + url);
		}
		return uri.getScheme() + "://" + uri.getHost() + ((uri.getPort() != -1) ? ":" + uri.getPort() : "");
	}

}
