package io.oea.app.login;

import io.oea.core.access.CredentialStore;
import io.oea.core.access.PasskeyStore;
import io.oea.infrastructure.webauthn.PasskeyRejectedException;
import io.oea.infrastructure.webauthn.WebAuthnVerifier;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Challenges and assertions of passkeys (design D2, D4, D5 of us-022-passkeys): the WebAuthn
 * challenges in {@code webauthn_challenges}, the request options, and the verification of an
 * assertion against an active passkey with the counter update.
 */
@Service
class Passkeys {

	static final Duration LOGIN_VALIDITY = Duration.ofMinutes(5);

	static final Duration REGISTRATION_VALIDITY = Duration.ofMinutes(10);

	static final long TIMEOUT_MILLISECONDS = 300_000;

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

	private final PasskeyStore store;

	private final CredentialStore credentials;

	private final WebAuthnVerifier verifier;

	private final Clock clock;

	Passkeys(PasskeyStore store, CredentialStore credentials, WebAuthnVerifier verifier, Clock clock) {
		this.store = store;
		this.credentials = credentials;
		this.verifier = verifier;
		this.clock = clock;
	}

	/** A stored challenge. */
	record Challenge(UUID id, byte[] value, Long personId, UUID loginTransactionId, String label, Instant expiresAt) {
	}

	/** The person and the passkey of a verified assertion. */
	record Verified(long personId, UUID credentialId) {
	}

	/**
	 * The relying party.
	 * @throws PasskeysUnavailableException without a relying party (D1)
	 */
	WebAuthnVerifier.RelyingParty relyingParty() {
		return this.verifier.relyingParty().orElseThrow(PasskeysUnavailableException::new);
	}

	boolean available() {
		return this.verifier.relyingParty().isPresent();
	}

	/** Stores a new challenge; expired challenges are deleted at the same time (D2). */
	Challenge newChallenge(String purpose, Long personId, UUID loginTransactionId, String label, Duration validity) {
		Instant now = this.clock.instant();
		this.store.deleteExpiredChallenges(now);
		byte[] value = new byte[32];
		RANDOM.nextBytes(value);
		Challenge challenge = new Challenge(UUID.randomUUID(), value, personId, loginTransactionId, label,
				now.plus(validity));
		this.store.insertChallenge(new PasskeyStore.Challenge(challenge.id(), value, personId, loginTransactionId, label,
				challenge.expiresAt()), purpose, now);
		return challenge;
	}

	/** Takes an unexpired challenge of the purpose; it is deleted in any case (single use). */
	Optional<Challenge> consume(UUID id, String purpose) {
		if (id == null) {
			return Optional.empty();
		}
		Instant now = this.clock.instant();
		return this.store.consumeChallenge(id, purpose)
			.map((c) -> new Challenge(c.id(), c.value(), c.personId(), c.loginTransactionId(), c.label(),
					c.expiresAt()))
			.stream()
			.filter((challenge) -> challenge.expiresAt().isAfter(now))
			.findFirst();
	}

	/** Request options (WebAuthn Level 3 JSON) with user verification required. */
	Map<String, Object> requestOptions(Challenge challenge, List<byte[]> allowCredentials) {
		Map<String, Object> options = new LinkedHashMap<>();
		options.put("challenge", base64url(challenge.value()));
		options.put("rpId", relyingParty().id());
		options.put("timeout", TIMEOUT_MILLISECONDS);
		options.put("userVerification", "required");
		options.put("allowCredentials", descriptors(allowCredentials));
		return options;
	}

	/** The WebAuthn credential IDs of the active passkeys of a person. */
	List<byte[]> credentialIds(long personId) {
		return this.store.activeCredentialIds(personId);
	}

	boolean hasActivePasskey(long personId) {
		return !credentialIds(personId).isEmpty();
	}

	/**
	 * Verifies an assertion against the active passkey it names, and records its use.
	 * @param expectedPersonId the person the passkey must belong to, or {@code null} for any
	 * @throws PasskeyRejectedException for any failed check
	 */
	Verified verify(String assertionJson, Challenge challenge, Long expectedPersonId) {
		WebAuthnVerifier.Presented presented = this.verifier.presented(assertionJson);
		PasskeyStore.StoredPasskey stored = this.store.lockActivePasskey(presented.credentialId())
			.orElseThrow(() -> new PasskeyRejectedException("unknown passkey"));
		if (expectedPersonId != null && expectedPersonId != stored.personId()) {
			throw new PasskeyRejectedException("passkey of another person");
		}
		if (presented.userHandle() != null && !Arrays.equals(presented.userHandle(), stored.userHandle())) {
			throw new PasskeyRejectedException("user handle does not match");
		}
		WebAuthnVerifier.Asserted asserted = this.verifier.verifyAuthentication(assertionJson, challenge.value(),
				new WebAuthnVerifier.StoredPasskey(stored.attestedCredentialData(), stored.signCount(), stored.backupEligible(),
						stored.backedUp()));
		this.store.updateAssertion(stored.credentialId(), asserted.signCount(), asserted.backedUp());
		this.credentials.markUsed(stored.credentialId(), this.clock.instant());
		return new Verified(stored.personId(), stored.credentialId());
	}

	static String base64url(byte[] value) {
		return BASE64URL.encodeToString(value);
	}

	static List<Map<String, Object>> descriptors(List<byte[]> credentialIds) {
		List<Map<String, Object>> descriptors = new ArrayList<>();
		for (byte[] id : credentialIds) {
			descriptors.add(Map.of("type", "public-key", "id", base64url(id)));
		}
		return descriptors;
	}

}
