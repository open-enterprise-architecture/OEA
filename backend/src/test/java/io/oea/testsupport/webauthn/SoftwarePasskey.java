package io.oea.testsupport.webauthn;

import com.webauthn4j.data.AttestationConveyancePreference;
import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.AuthenticatorSelectionCriteria;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialDescriptor;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.PublicKeyCredentialRpEntity;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.data.ResidentKeyRequirement;
import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.test.authenticator.webauthn.NoneAttestationAuthenticator;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor;
import com.webauthn4j.test.client.ClientPlatform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.cbor.CBORMapper;

/**
 * A software passkey for tests without hardware (design D7 of us-022-passkeys): a WebAuthn4J
 * emulator that answers the creation and request options of OEA with the WebAuthn Level 3 JSON a
 * browser would send. It stores its discoverable credentials, like a platform authenticator.
 */
public final class SoftwarePasskey {

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private final ClientPlatform platform;

	public SoftwarePasskey(String origin) {
		this.platform = new ClientPlatform(new Origin(origin),
				new WebAuthnAuthenticatorAdaptor(new NoneAttestationAuthenticator()));
	}

	/** Answers as if the browser ran on another origin (phishing site). */
	public void moveTo(String origin) {
		this.platform.setOrigin(new Origin(origin));
	}

	/** The registration response for creation options of OEA. */
	public String register(JsonNode options, boolean userVerification) {
		return register(options, userVerification, options.path("rp").path("id").asString());
	}

	/** The registration response, with the relying party ID the authenticator believes in. */
	public String register(JsonNode options, boolean userVerification, String rpId) {
		JsonNode user = options.path("user");
		PublicKeyCredentialCreationOptions creation = new PublicKeyCredentialCreationOptions(
				new PublicKeyCredentialRpEntity(rpId, options.path("rp").path("name").asString()),
				new PublicKeyCredentialUserEntity(decode(user.path("id")), user.path("name").asString(),
						user.path("displayName").asString()),
				new DefaultChallenge(decode(options.path("challenge"))),
				List.of(new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY,
						COSEAlgorithmIdentifier.ES256)),
				options.path("timeout").asLong(), descriptors(options.path("excludeCredentials")),
				new AuthenticatorSelectionCriteria(null, true, ResidentKeyRequirement.REQUIRED,
						userVerification ? UserVerificationRequirement.REQUIRED : UserVerificationRequirement.DISCOURAGED),
				AttestationConveyancePreference.NONE, null);
		PublicKeyCredential<AuthenticatorAttestationResponse, ?> credential = this.platform.create(creation);
		ObjectNode json = credential(credential.getRawId());
		ObjectNode response = json.putObject("response");
		response.put("clientDataJSON", encode(credential.getResponse().getClientDataJSON()));
		byte[] attestationObject = credential.getResponse().getAttestationObject();
		response.put("attestationObject",
				encode(userVerification ? attestationObject : withoutUserVerification(attestationObject)));
		response.putArray("transports").add("internal");
		return json.toString();
	}

	/**
	 * The emulator always verifies the user at a registration; without a signature over the
	 * authenticator data (attestation {@code none}) the flag UV can be cleared afterwards.
	 */
	@SuppressWarnings("unchecked")
	private static byte[] withoutUserVerification(byte[] attestationObject) {
		CBORMapper cbor = new CBORMapper();
		Map<String, Object> object = cbor.readValue(attestationObject, LinkedHashMap.class);
		byte[] authenticatorData = ((byte[]) object.get("authData")).clone();
		authenticatorData[32] &= (byte) ~0x04;
		object.put("authData", authenticatorData);
		return cbor.writeValueAsBytes(object);
	}

	/** The authentication response for request options of OEA. */
	public String authenticate(JsonNode options, boolean userVerification) {
		PublicKeyCredentialRequestOptions request = new PublicKeyCredentialRequestOptions(
				new DefaultChallenge(decode(options.path("challenge"))), options.path("timeout").asLong(),
				options.path("rpId").asString(), descriptors(options.path("allowCredentials")),
				userVerification ? UserVerificationRequirement.REQUIRED : UserVerificationRequirement.DISCOURAGED, null);
		PublicKeyCredential<AuthenticatorAssertionResponse, ?> credential = this.platform.get(request);
		ObjectNode json = credential(credential.getRawId());
		ObjectNode response = json.putObject("response");
		response.put("clientDataJSON", encode(credential.getResponse().getClientDataJSON()));
		response.put("authenticatorData", encode(credential.getResponse().getAuthenticatorData()));
		response.put("signature", encode(credential.getResponse().getSignature()));
		if (credential.getResponse().getUserHandle() != null) {
			response.put("userHandle", encode(credential.getResponse().getUserHandle()));
		}
		return json.toString();
	}

	private static ObjectNode credential(byte[] rawId) {
		ObjectNode json = JsonMapper.shared().createObjectNode();
		json.put("id", encode(rawId));
		json.put("rawId", encode(rawId));
		json.put("type", "public-key");
		json.put("authenticatorAttachment", "platform");
		json.putObject("clientExtensionResults");
		return json;
	}

	private static List<PublicKeyCredentialDescriptor> descriptors(JsonNode list) {
		List<PublicKeyCredentialDescriptor> descriptors = new ArrayList<>();
		for (JsonNode descriptor : list) {
			descriptors.add(new PublicKeyCredentialDescriptor(PublicKeyCredentialType.PUBLIC_KEY,
					decode(descriptor.path("id")), null));
		}
		return descriptors;
	}

	private static byte[] decode(JsonNode value) {
		return DECODER.decode(value.asString());
	}

	private static String encode(byte[] value) {
		return ENCODER.encodeToString(value);
	}

}
