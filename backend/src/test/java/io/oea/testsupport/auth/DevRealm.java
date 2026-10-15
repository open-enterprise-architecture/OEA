package io.oea.testsupport.auth;

import io.oea.testsupport.containers.OeaKeycloak;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read access to the development realm file, the single source of the test users,
 * their development passwords, TOTP secrets, client secrets and group identifiers.
 */
public final class DevRealm {

	private static final JsonNode REALM = read();

	private DevRealm() {
	}

	public static String password(String username) {
		return credential(username, "password").path("value").asString();
	}

	/** TOTP secret of the user (raw key as stored by Keycloak), or {@code null} without a second factor. */
	public static String totpSecret(String username) {
		JsonNode otp = credential(username, "otp");
		if (otp.isMissingNode()) {
			return null;
		}
		return JsonMapper.shared().readTree(otp.path("secretData").asString()).path("value").asString();
	}

	public static String clientSecret(String clientId) {
		for (JsonNode client : REALM.path("clients")) {
			if (clientId.equals(client.path("clientId").asString())) {
				return client.path("secret").isMissingNode() ? null : client.path("secret").asString();
			}
		}
		throw new IllegalArgumentException("Unknown client " + clientId);
	}

	/** Immutable group identifier as emitted in the {@code groups} claim. */
	public static String groupId(String groupName) {
		for (JsonNode group : REALM.path("groups")) {
			if (groupName.equals(group.path("name").asString())) {
				return group.path("id").asString();
			}
		}
		throw new IllegalArgumentException("Unknown group " + groupName);
	}

	private static JsonNode credential(String username, String type) {
		for (JsonNode user : REALM.path("users")) {
			if (username.equals(user.path("username").asString())) {
				for (JsonNode credential : user.path("credentials")) {
					if (type.equals(credential.path("type").asString())) {
						return credential;
					}
				}
				return JsonMapper.shared().missingNode();
			}
		}
		throw new IllegalArgumentException("Unknown user " + username);
	}

	private static JsonNode read() {
		try {
			return JsonMapper.shared().readTree(Files.readString(OeaKeycloak.REALM_FILE));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
